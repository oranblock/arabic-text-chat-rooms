'use strict';
/**
 * High-performance real-time chat server for the Arabic / Iraqi rooms app.
 * Socket.io + JSON-file store (lib/store.js). Covers Ali's 9 requirements:
 * accounts, ranks, anti-spam, auto-mute, moderation, filter, ghost mode,
 * strict device ban, in-chat YouTube sync, permanent logs.
 */
const fs = require('fs');
const path = require('path');
const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const cors = require('cors');

const store = require('./lib/store');
const auth = require('./lib/auth');
const filter = require('./lib/filter');
const push = require('./lib/push');
const { QuizBot } = require('./lib/bot');
const { createAdminRouter, handleAdminCommand } = require('./lib/admin_api');

const app = express();
app.set('trust proxy', true);
app.use(cors());
app.use('/api/upload', express.json({ limit: '10mb' }));
app.use(express.json({ limit: '64kb' }));
app.use(express.urlencoded({ extended: true, limit: '64kb' }));
const server = http.createServer(app);
const io = new Server(server, {
  cors: { origin: '*', methods: ['GET', 'POST'] },
  maxHttpBufferSize: 1e5, // 100 KB max packet frame size
  pingTimeout: 20000,
  pingInterval: 25000
});
const PORT = process.env.PORT || 3000;

const RANKS = ['REGULAR', 'BOT', 'VIP_DIAMOND', 'MODERATOR', 'ADMIN', 'OWNER'];
const rank = (u) => (u ? RANKS.indexOf(u.rank) : -1);
const isStaff = (u) => u && (u.rank === 'MODERATOR' || u.rank === 'ADMIN' || u.rank === 'OWNER');
const isVip = (u) => u && (u.rank === 'VIP_DIAMOND' || isStaff(u));
const now = () => Date.now();
const hhmm = () => new Date().toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });

function getLinkedAccounts(user) {
  if (!user) return [];
  const linkedIds = new Set();
  for (const dev of (user.devices || [])) {
    const arr = store.state.deviceAccounts[dev] || [];
    for (const uid of arr) {
      if (uid !== user.id) linkedIds.add(uid);
    }
  }
  if (user.lastIp && store.state.ipAccounts && store.state.ipAccounts[user.lastIp]) {
    for (const uid of store.state.ipAccounts[user.lastIp]) {
      if (uid !== user.id) linkedIds.add(uid);
    }
  }
  const out = [];
  for (const uid of linkedIds) {
    const o = store.state.users[uid];
    if (o) {
      out.push({
        id: o.id,
        name: o.displayName || o.name,
        loginName: o.name,
        rank: o.rank,
        isMuted: !!o.isMuted,
        isGhost: !!o.isGhost,
        createdAt: o.createdAt || 0
      });
    }
  }
  return out;
}

function getClientIp(reqOrSocket) {
  const headers = reqOrSocket.headers || reqOrSocket.handshake?.headers || {};

  // 1. Cloudflare's verified connecting IP header (tamper-proof via Cloudflare edge)
  const cf = headers['cf-connecting-ip'];
  if (cf) return String(cf).trim();

  // 2. Direct remote address
  const rawRemote = reqOrSocket.handshake?.address || reqOrSocket.socket?.remoteAddress || reqOrSocket.ip || '127.0.0.1';

  // 3. Only trust X-Forwarded-For if connection strictly comes from local reverse proxy (Nginx/Cloudflared)
  const isLocalProxy = rawRemote.includes('127.0.0.1') || rawRemote.includes('::1') || rawRemote === 'localhost';
  if (isLocalProxy && headers['x-forwarded-for']) {
    const first = String(headers['x-forwarded-for']).split(',')[0].trim();
    if (first) return first;
  }

  return rawRemote;
}

const online = new Map();             // userId -> { socketId, roomId }
const lastSent = new Map();           // userId -> { at, text }
const registerAttempts = new Map();   // clientIp -> [timestamp]
const guestIpAttempts = new Map();    // clientIp -> [timestamp]
const ipSockets = new Map();          // clientIp -> Set<socketId>
const MAX_SOCKETS_PER_IP = 10;
const MAX_GLOBAL_GUESTS = 200;
const GUEST_TTL_MS = 15 * 60 * 1000;  // 15 minutes max guest lifespan

// Self-healing periodic cleanup of ipSockets to prevent any zombie tracking leakage
setInterval(() => {
  for (const [ip, set] of ipSockets.entries()) {
    for (const sid of set) {
      if (!io.sockets.sockets.has(sid)) {
        set.delete(sid);
      }
    }
    if (set.size === 0) {
      ipSockets.delete(ip);
    }
  }
}, 30000);

function publicUser(u) {
  return {
    id: u.id, name: u.displayName || u.name, loginName: u.name, avatarUrl: u.avatarUrl || '', rank: u.rank,
    customHexColor: u.customHexColor || null, country: u.country || 'العراق',
    bio: u.bio || '', age: u.age || null, gender: u.gender || '',
    isMuted: !!u.isMuted, isGhost: !!u.isGhost, status: u.status || 'online',
    isGuest: !!u.isGuest, guestExpiresAt: u.guestExpiresAt || null,
    lockPrivate: !!u.lockPrivate, muteNotifications: !!u.muteNotifications,
    oldNames: u.oldNames || [],
    online: online.has(u.id)
  };
}

function roomUsers(roomId) {
  const out = [];
  for (const [uid, p] of online) {
    if (p.roomId === roomId) {
      const u = store.state.users[uid];
      if (u && !u.isGhost) out.push(publicUser(u));
    }
  }
  const roomBots = quizBot.getBotsForRoom ? quizBot.getBotsForRoom(roomId) : [];
  for (const b of roomBots) {
    out.push(b);
  }
  return out;
}

function broadcastUserList(roomId) {
  const users = roomUsers(roomId);
  io.to(roomId).emit('user_list', { roomId, users, online: users.length });
}

function roomsSummary() {
  return Object.values(store.state.rooms).map(r => ({
    id: r.id, title: r.title, description: r.description, topic: r.topic || '',
    lockPublic: !!r.lockPublic, lockPrivate: !!r.lockPrivate,
    requiredRank: r.requiredRank || 'REGULAR',
    supervisorId: r.supervisorId || null,
    online: roomUsers(r.id).length
  }));
}

async function pushToUser(user, title, body, data) {
  if (!push.enabled() || !user || !user.pushTokens || user.pushTokens.length === 0) return;
  const { invalid } = await push.send(user.pushTokens, title, body, data || {});
  if (invalid.length) {
    user.pushTokens = user.pushTokens.filter(t => !invalid.includes(t));
    store.flush();
  }
}

function makeMessage(u, roomId, text, extra = {}) {
  return {
    id: store.nextId('m'),
    roomId,
    senderId: u.id,
    senderName: u.displayName || u.name,
    senderAvatar: u.avatarUrl || '',
    senderRank: u.rank,
    senderGender: u.gender || '',
    customHexColor: u.customHexColor || null,
    text,
    timestamp: hhmm(),
    at: now(),
    isGhost: false,
    ...extra
  };
}

const quizBot = new QuizBot(io, store, makeMessage);
quizBot.start();

// Purge expired guest accounts (temporary, auto-deleted after 15 min TTL).
setInterval(() => {
  const t = now();
  let changed = false;
  for (const [id, u] of Object.entries(store.state.users)) {
    if (u.isGuest && u.guestExpiresAt && u.guestExpiresAt < t) {
      const p = online.get(id);
      if (p) { io.to(p.socketId).emit('force_disconnect', { reason: 'انتهت مدة الحساب المؤقت (زائر)' }); online.delete(id); }
      delete store.state.names[(u.name || '').trim().toLowerCase()];
      for (const [tok, uid] of Object.entries(store.state.tokens)) if (uid === id) delete store.state.tokens[tok];
      delete store.state.users[id];
      changed = true;
    }
  }
  if (changed) store.flush();
}, 60 * 1000);

// Serve uploaded media (images & voice notes)
app.use('/uploads', express.static(path.join(__dirname, 'public/uploads')));

// Media Upload API (Base64 JSON for Images & Voice Notes)
app.post('/api/upload', (req, res) => {
  const { data, filename, type } = req.body || {};
  if (!data || typeof data !== 'string') {
    return res.status(400).json({ ok: false, error: 'بيانات الملف مفقودة' });
  }
  if (data.length > 8 * 1024 * 1024) {
    return res.status(413).json({ ok: false, error: 'حجم الملف كبير جداً (الحد الأقصى 5 ميجابايت)' });
  }

  const mediaType = type === 'audio' ? 'audio' : 'image';
  const ext = mediaType === 'audio' ? '.m4a' : (filename && path.extname(filename).toLowerCase()) || '.jpg';
  const allowedExts = ['.jpg', '.jpeg', '.png', '.webp', '.gif', '.m4a', '.aac', '.mp3', '.wav', '.ogg'];
  const safeExt = allowedExts.includes(ext) ? ext : (mediaType === 'audio' ? '.m4a' : '.jpg');

  const base64Data = data.replace(/^data:[^;]+;base64,/, '');
  const buffer = Buffer.from(base64Data, 'base64');
  const uniqueName = `media_${Date.now()}_${Math.random().toString(36).substring(2, 8)}${safeExt}`;
  const filePath = path.join(__dirname, 'public/uploads', uniqueName);

  fs.writeFile(filePath, buffer, (err) => {
    if (err) {
      console.error('Upload write error:', err);
      return res.status(500).json({ ok: false, error: 'فشل حفظ الملف على السيرفر' });
    }
    res.json({ ok: true, url: `/uploads/${uniqueName}`, mediaType });
  });
});

const adminRouter = createAdminRouter({
  store, auth, online, io, quizBot, publicUser, roomsSummary, isStaff, rank, RANKS, now, makeMessage, getLinkedAccounts
});
app.use('/api/admin', adminRouter);
app.use('/admin', express.static(path.join(__dirname, 'public/admin')));
app.get('/admin', (_req, res) => res.sendFile(path.join(__dirname, 'public/admin/index.html')));

app.get('/player/:videoId', (req, res) => {
  const videoId = (req.params.videoId || '').replace(/[^A-Za-z0-9_-]/g, '');
  const start = parseInt(req.query.start || req.query.t || '0', 10) || 0;
  if (!videoId) return res.status(400).send('معرف فيديو غير صالح');
  res.setHeader('Content-Type', 'text/html; charset=utf-8');
  res.send(`<!DOCTYPE html>
<html lang="ar">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
  <meta name="referrer" content="origin">
  <title>YouTube Player</title>
  <style>
    * { margin:0; padding:0; box-sizing:border-box; }
    html, body { width:100%; height:100%; background:#000; overflow:hidden; }
    #player { width:100%; height:100%; position:absolute; top:0; left:0; }
    #end-overlay {
      display:none; position:absolute; top:0; left:0; width:100%; height:100%;
      background:#0f172a; color:#fff; flex-direction:column;
      align-items:center; justify-content:center; text-align:center; padding:16px;
      font-family:system-ui, -apple-system, sans-serif; z-index:20;
    }
  </style>
</head>
<body>
  <div id="player"></div>
  <div id="end-overlay">
    <div style="font-size:22px; margin-bottom:8px;">✅ انتهى الفيديو</div>
    <div style="font-size:13px; color:#94a3b8;">تم إيقاف المشغل التلقائي للغرفة</div>
  </div>

  <script src="https://www.youtube.com/iframe_api"></script>
  <script>
    var player;
    function onYouTubeIframeAPIReady() {
      player = new YT.Player('player', {
        height: '100%',
        width: '100%',
        videoId: '${videoId}',
        playerVars: {
          autoplay: 1,
          playsinline: 1,
          controls: 1,
          rel: 0,
          modestbranding: 1,
          start: ${start},
          origin: window.location.origin
        },
        events: {
          'onReady': onPlayerReady,
          'onStateChange': onPlayerStateChange
        }
      });
    }

    function onPlayerReady(event) {
      try {
        var dur = player.getDuration();
        if (dur > 0 && ${start} >= dur) {
          onPlayerStateChange({ data: 0 });
        }
      } catch(e) {}
    }

    function onPlayerStateChange(event) {
      // 0 = YT.PlayerState.ENDED
      if (event.data === 0) {
        try {
          document.getElementById('player').style.display = 'none';
          document.getElementById('end-overlay').style.display = 'flex';
          window.location.href = 'ali-chat://video-ended?videoId=${videoId}';
        } catch(e) {}
      }
    }
  </script>
</body>
</html>`);
});

io.use((socket, next) => {
  const clientIp = getClientIp(socket);
  socket.clientIp = clientIp;

  // 1. Connection Exhaustion Defense: max 10 concurrent active sockets per IP
  const activeForIp = ipSockets.get(clientIp) || new Set();
  if (activeForIp.size >= MAX_SOCKETS_PER_IP) {
    return next(new Error('CONN_LIMIT: تم تجاوز الحد الأقصى للاتصالات المتزامنة من عنوانك'));
  }
  activeForIp.add(socket.id);
  ipSockets.set(clientIp, activeForIp);

  // 2. Validate deviceHash format (prevent malformed/oversized payloads)
  let rawHash = socket.handshake.query.deviceHash || '';
  if (typeof rawHash === 'string') {
    rawHash = rawHash.trim();
    if (rawHash.length < 8 || rawHash.length > 128 || !/^[a-zA-Z0-9_-]+$/.test(rawHash)) {
      rawHash = '';
    }
  } else {
    rawHash = '';
  }
  socket.deviceHash = rawHash;

  // 3. Hardware device ban check
  if (rawHash && store.state.bannedDevices[rawHash]) {
    activeForIp.delete(socket.id);
    if (activeForIp.size === 0) ipSockets.delete(clientIp);
    return next(new Error('DEVICE_BANNED: تم حظر عتاد هذا الجهاز نهائياً'));
  }
  next();
});

function checkAndExpireRoomVideo(room) {
  if (!room || !room.youtubeId) return;
  if (room.isWelcome) return; // Welcome videos don't auto-expire

  const elapsedSec = room.youtubeStartedAt ? Math.max(0, Math.floor((now() - room.youtubeStartedAt) / 1000)) : 999999;
  const maxDurationSec = room.youtubeDurationSec || 600; // Default max 10 minutes for in-chat video

  if (elapsedSec >= maxDurationSec) {
    const q = room.youtubeQueue || [];
    if (q.length > 0) {
      const nextItem = q.shift();
      room.youtubeId = nextItem.videoId;
      room.youtubeTitle = nextItem.title || ('فيديو بواسطة ' + nextItem.by);
      room.youtubeStartedAt = now();
      room.youtubeStartedBy = nextItem.by;
      room.isWelcome = false;
      store.save();
      io.to(room.id).emit('youtube_updated', {
        videoId: nextItem.videoId,
        videoTitle: room.youtubeTitle,
        startedAt: room.youtubeStartedAt,
        startedBy: nextItem.by,
        offset: 0,
        status: 'play',
        by: nextItem.by,
        isWelcome: false
      });
      io.to(room.id).emit('system_message', {
        roomId: room.id,
        text: `🎬 بدأ تلقائياً الفيديو التالي في قائمة الانتظار: ${room.youtubeTitle} (بواسطة ${nextItem.by}) 🎵`,
        at: now()
      });
    } else if (room.welcomeVideoId) {
      room.youtubeId = room.welcomeVideoId;
      room.youtubeTitle = room.welcomeVideoTitle || ('فيديو ترحيبي - ' + room.title);
      room.youtubeStartedAt = now();
      room.youtubeStartedBy = 'فيديو ترحيبي';
      room.isWelcome = true;
      store.save();
      io.to(room.id).emit('youtube_updated', {
        videoId: room.welcomeVideoId,
        videoTitle: room.youtubeTitle,
        startedAt: room.youtubeStartedAt,
        startedBy: 'فيديو ترحيبي',
        offset: 0,
        status: 'play',
        by: 'نظام الغرفة',
        isWelcome: true
      });
    } else {
      room.youtubeId = '';
      room.youtubeTitle = '';
      room.youtubeStartedAt = 0;
      room.youtubeStartedBy = '';
      room.isWelcome = false;
      store.save();
      io.to(room.id).emit('youtube_updated', {
        videoId: '',
        videoTitle: '',
        startedAt: 0,
        startedBy: '',
        offset: 0,
        status: 'ended',
        by: 'system',
        isWelcome: false
      });
      io.to(room.id).emit('system_message', {
        roomId: room.id,
        text: `🏁 اكتمل عرض الفيديو وتوقف المشغل بنجاح. أرسل /yt أو /q لتشغيل فيديو جديد 🎵`,
        at: now()
      });
    }
  }
}

// Proactive expiration monitor: runs every 15 seconds
setInterval(() => {
  try {
    Object.values(store.state.rooms || {}).forEach(checkAndExpireRoomVideo);
  } catch (e) {}
}, 15000);

io.on('connection', (socket) => {
  socket.data.userId = null;

  // Zombie socket / Slowloris defense: 20 seconds auth window
  const authTimer = setTimeout(() => {
    if (!socket.data.userId) {
      try {
        socket.emit('error_alert', { message: 'انتهت مهلة التحقق والمصادقة (Auth Timeout)' });
        socket.disconnect(true);
      } catch (e) {}
    }
  }, 20000);

  const currentUser = () => (socket.data.userId ? store.state.users[socket.data.userId] : null);
  const fail = (cb, message) => { if (typeof cb === 'function') cb({ ok: false, error: message }); };

  socket.on('register', ({ name, password, avatarUrl, guest, isGuest: explicitGuest, age, gender, country, bio, isGhost, lockPrivate, muteNotifications } = {}, cb) => {
    const clientIp = socket.clientIp || getClientIp(socket);
    const nowTime = now();

    // 1. IP Registration Rate Limiting (Max 3 accounts per 5 minutes per IP)
    const attempts = (registerAttempts.get(clientIp) || []).filter(t => nowTime - t < 5 * 60 * 1000);
    if (attempts.length >= 3) {
      return fail(cb, 'تم تجاوز حد محاولات التسجيل من هذا العنوان. يرجى الانتظار 5 دقائق.');
    }
    attempts.push(nowTime);
    registerAttempts.set(clientIp, attempts);

    const isGuest = !!guest || !!explicitGuest;
    // 2. Guest Flooding Shield
    if (isGuest) {
      const currentGuests = Object.values(store.state.users).filter(u => u.isGuest).length;
      if (currentGuests >= MAX_GLOBAL_GUESTS) {
        return fail(cb, 'وصل نظام حسابات الزوار للحد الأقصى حالياً، يرجى التسجيل بحساب رسمي.');
      }
      const guestAttempts = (guestIpAttempts.get(clientIp) || []).filter(t => nowTime - t < 10 * 60 * 1000);
      if (guestAttempts.length >= 2) {
        return fail(cb, 'تم تجاوز عدد حسابات الزائر المسموح بها من عنوانك مؤقتاً.');
      }
      guestAttempts.push(nowTime);
      guestIpAttempts.set(clientIp, guestAttempts);
    }

    // 3. Hardware device limit (Max 5 accounts per deviceHash)
    if (socket.deviceHash) {
      if (store.state.bannedDevices[socket.deviceHash]) return fail(cb, 'هذا الجهاز محظور نهائياً');
      const existing = store.state.deviceAccounts[socket.deviceHash] || [];
      if (existing.length >= 5 && !isGuest) {
        return fail(cb, 'تم تجاوز الحد الأقصى للحسابات المسموح بإنشائها من هذا الجهاز (5 حسابات كحد أقصى)');
      }
    }

    if (!auth.validName(name)) return fail(cb, 'الاسم يجب أن يكون بين 2 و50 حرفاً');
    if (!isGuest && (typeof password !== 'string' || password.length < 3)) return fail(cb, 'الرمز السري قصير جداً');
    const key = (name || '').trim().toLowerCase();
    if (store.state.names[key]) return fail(cb, 'الاسم مستخدم مسبقاً');

    const { salt, hash } = isGuest ? { salt: '', hash: '' } : auth.hashPassword(password);
    const isFirst = Object.keys(store.state.users).length === 0;
    const AUTO_COLORS = ['#f3d5d5', '#e9f4d4', '#d5eef5', '#e9dcee', '#f3e6d4', '#fad5f6', '#ece9ff', '#FD62BE'];
    const id = store.nextId(isGuest ? 'guest_' : 'u_');
    const user = {
      id, name: name.trim(), salt, hash,
      avatarUrl: avatarUrl || '',
      rank: isFirst ? 'OWNER' : 'REGULAR',
      customHexColor: isFirst ? null : AUTO_COLORS[Math.floor(Math.random() * AUTO_COLORS.length)],
      country: country || 'IQ',
      age: Number(age) || null,
      gender: gender || '',
      bio: bio || '',
      isMuted: process.env.AUTO_MUTE === 'true' ? !isFirst : false,
      isGhost: !!isGhost, status: 'online',
      lockPrivate: !!lockPrivate, muteNotifications: !!muteNotifications,
      isGuest, guestExpiresAt: isGuest ? now() + GUEST_TTL_MS : null,
      oldNames: [], createdAt: now(), lastIp: clientIp,
      devices: socket.deviceHash ? [socket.deviceHash] : []
    };
    store.state.users[id] = user;
    store.state.names[key] = id;
    if (socket.deviceHash) {
      const list = store.state.deviceAccounts[socket.deviceHash] || (store.state.deviceAccounts[socket.deviceHash] = []);
      if (!list.includes(id)) list.push(id);
    }
    if (!store.state.ipAccounts) store.state.ipAccounts = {};
    const ipList = store.state.ipAccounts[clientIp] || (store.state.ipAccounts[clientIp] = []);
    if (!ipList.includes(id)) ipList.push(id);

    const token = auth.newToken();
    store.state.tokens[token] = id;
    store.flush();
    clearTimeout(authTimer);
    if (typeof cb === 'function') cb({ ok: true, token, user: publicUser(user) });
  });

  socket.on('login', ({ name, password, token, isGhost, lockPrivate, muteNotifications } = {}, cb) => {
    let user = null;
    if (token && store.state.tokens[token]) user = store.state.users[store.state.tokens[token]];
    if (!user) {
      if (!auth.validName(name)) return fail(cb, 'بيانات دخول غير صحيحة');
      const id = store.state.names[(name || '').trim().toLowerCase()];
      const candidate = id && store.state.users[id];
      if (!candidate || !auth.verifyPassword(password || '', candidate.salt, candidate.hash)) {
        return fail(cb, 'الاسم أو الرمز السري غير صحيح');
      }
      user = candidate;
    }
    const clientIp = getClientIp(socket);
    user.lastIp = clientIp;
    if (isGhost !== undefined) user.isGhost = !!isGhost;
    if (lockPrivate !== undefined) user.lockPrivate = !!lockPrivate;
    if (muteNotifications !== undefined) user.muteNotifications = !!muteNotifications;

    if (socket.deviceHash && !(user.devices || []).includes(socket.deviceHash)) {
      (user.devices || (user.devices = [])).push(socket.deviceHash);
      const list = store.state.deviceAccounts[socket.deviceHash] || (store.state.deviceAccounts[socket.deviceHash] = []);
      if (!list.includes(user.id)) list.push(user.id);
    }
    if (!store.state.ipAccounts) store.state.ipAccounts = {};
    const ipList = store.state.ipAccounts[clientIp] || (store.state.ipAccounts[clientIp] = []);
    if (!ipList.includes(user.id)) ipList.push(user.id);

    const newTok = token && store.state.tokens[token] ? token : auth.newToken();
    store.state.tokens[newTok] = user.id;
    store.flush();
    clearTimeout(authTimer);
    if (typeof cb === 'function') cb({ ok: true, token: newTok, user: publicUser(user) });
  });

  socket.on('join_room', ({ token, roomId } = {}, cb) => {
    const uid = token && store.state.tokens[token];
    const user = uid && store.state.users[uid];
    if (!user) return fail(cb, 'الجلسة منتهية، سجل الدخول من جديد');
    const room = store.state.rooms[roomId] || store.state.rooms.iraq;

    // Rank / Permission barrier for restricted rooms
    if (room.requiredRank && rank(user) < RANKS.indexOf(room.requiredRank) && !isStaff(user)) {
      return fail(cb, `هذه الغرفة مخصصة لرتبة ${room.requiredRank} وما فوق.`);
    }

    const prev = online.get(user.id);
    if (prev && prev.roomId && prev.roomId !== room.id) {
      socket.leave(prev.roomId);
      broadcastUserList(prev.roomId);
    }
    socket.data.userId = user.id;
    socket.join(room.id);
    online.set(user.id, { socketId: socket.id, roomId: room.id });
    user.status = 'online';

    checkAndExpireRoomVideo(room);
    const history = store.state.messages[room.id] || [];
    if (typeof cb === 'function') {
      cb({
        ok: true,
        room: {
          id: room.id,
          title: room.title,
          topic: room.topic || '',
          lockPublic: !!room.lockPublic,
          lockPrivate: !!room.lockPrivate,
          requiredRank: room.requiredRank || 'REGULAR',
          supervisorId: room.supervisorId || null,
          youtubeId: room.youtubeId || '',
          youtubeTitle: room.youtubeTitle || '',
          youtubeStartedAt: room.youtubeStartedAt || 0,
          youtubeStartedBy: room.youtubeStartedBy || '',
          isWelcome: !!room.isWelcome,
          youtubeOffset: room.youtubeStartedAt ? Math.max(0, Math.floor((now() - room.youtubeStartedAt) / 1000)) : 0,
          youtubeQueue: room.youtubeQueue || []
        },
        me: publicUser(user),
        messages: history,
        users: roomUsers(room.id)
      });
    }
    if (!user.isGhost) {
      io.to(room.id).emit('system_message', { roomId: room.id, text: `${user.displayName || user.name} دخل الغرفة`, at: now() });
      broadcastUserList(room.id);
    }
    io.emit('rooms', { rooms: roomsSummary() });
  });

  socket.on('list_rooms', (_p, cb) => { if (typeof cb === 'function') cb({ ok: true, rooms: roomsSummary() }); });

  socket.on('send_message', ({ text, mediaType, mediaUrl, audioDuration } = {}) => {
    const user = currentUser();
    if (!user) return;
    const p = online.get(user.id);
    if (!p || !p.roomId) return;
    const room = store.state.rooms[p.roomId];
    if (!room) return;

    if (user.isMuted) {
      return socket.emit('system_message', { roomId: room.id, text: 'أنت مكتوم ولا يمكنك الإرسال', at: now() });
    }
    if (room.lockPublic && !isStaff(user)) {
      return socket.emit('system_message', { roomId: room.id, text: 'الشات مقفول حالياً من الإدارة', at: now() });
    }

    // In-chat slash admin commands (/mute, /unmute, /kick, /ban, /promote, /quiz, /broadcast, etc.)
    if (typeof text === 'string' && (text.trim().startsWith('/') || text.trim().startsWith('!'))) {
      const handled = handleAdminCommand(socket, user, room, text, {
        store, io, online, rank, isStaff, RANKS, publicUser, quizBot, now, roomsSummary
      });
      if (handled) return;
    }

    if (user.isMuted) return socket.emit('error_alert', { message: 'أنت مكتوم، انتظر موافقة المشرف' });
    if (room.lockPublic && !isStaff(user)) return socket.emit('error_alert', { message: 'الشات العام مقفل حالياً' });

    let cleanText = '';
    if (text && typeof text === 'string' && text.trim()) {
      const clean = filter.check(text);
      if (!clean.ok) return socket.emit('error_alert', { message: clean.reason });
      cleanText = clean.text;
    } else if (mediaType === 'image') {
      cleanText = '📷 صورة';
    } else if (mediaType === 'audio') {
      cleanText = '🎤 تسجيل صوتي';
    } else {
      return socket.emit('error_alert', { message: 'رسالة فارغة' });
    }

    // Validate mediaUrl
    let safeMediaUrl = null;
    let safeMediaType = null;
    let safeAudioDuration = null;
    if (typeof mediaUrl === 'string' && (mediaUrl.startsWith('/uploads/') || mediaUrl.startsWith('https://') || mediaUrl.startsWith('http://'))) {
      safeMediaUrl = mediaUrl.slice(0, 500);
      safeMediaType = mediaType === 'audio' ? 'audio' : 'image';
      if (safeMediaType === 'audio' && typeof audioDuration === 'number') {
        safeAudioDuration = Math.min(300, Math.max(1, Math.floor(audioDuration)));
      }
    }

    const last = lastSent.get(user.id);
    if (last && !isStaff(user)) {
      if (now() - last.at < 800) {
        user.isMuted = true; store.flush();
        io.to(room.id).emit('user_updated', { user: publicUser(user) });
        return socket.emit('error_alert', { message: 'تم كتمك تلقائياً بسبب الإغراق' });
      }
      if (now() - last.at < 2000) return socket.emit('error_alert', { message: 'يرجى الانتظار ثانيتين بين كل رسالة' });
      if (last.text === cleanText && !safeMediaUrl) {
        user.isMuted = true; store.flush();
        socket.emit('force_disconnect', { reason: 'تم طردك تلقائياً من النظام بسبب تكرار الكلام (Anti-Spam)' });
        socket.disconnect(true);
        return;
      }
    }
    lastSent.set(user.id, { at: now(), text: cleanText });

    const msg = makeMessage(user, room.id, cleanText, {
      mediaType: safeMediaType,
      mediaUrl: safeMediaUrl,
      audioDuration: safeAudioDuration
    });
    if (user.isGhost) {
      return socket.emit('new_message', msg);
    }
    store.pushMessage(room.id, msg);
    io.to(room.id).emit('new_message', msg);
    quizBot.checkAnswer(room.id, user, cleanText);

    const yt = filter.youtubeId(cleanText);
    if (yt) {
      room.youtubeId = yt;
      room.youtubeTitle = 'فيديو بواسطة ' + user.name;
      room.youtubeStartedAt = now();
      room.youtubeStartedBy = user.name;
      room.isWelcome = false;
      store.save();
      io.to(room.id).emit('youtube_updated', {
        videoId: yt,
        videoTitle: room.youtubeTitle,
        startedAt: room.youtubeStartedAt,
        startedBy: user.name,
        offset: 0,
        status: 'play',
        by: user.name,
        isWelcome: false
      });
    }
  });

  socket.on('private_send', ({ toUserId, text, mediaType, mediaUrl, audioDuration } = {}, cb) => {
    const user = currentUser();
    if (!user) return fail(cb, 'سجل الدخول');
    const target = store.state.users[toUserId];
    if (!target) return fail(cb, 'العضو غير موجود');
    if (!isVip(user)) return fail(cb, 'المراسلة الخاصة مغلقة للأعضاء الجدد — تفتح تلقائياً عند الحصول على رتبة مميز (VIP) والإدارة فما فوق');
    if (target.lockPrivate && !isStaff(user)) return fail(cb, 'هذا العضو قام بتعطيل استلام الرسائل الخاصة في إعداداته');
    const room = store.state.rooms[(online.get(user.id) || {}).roomId];
    if (room && room.lockPrivate && !isStaff(user)) return fail(cb, 'المحادثات الخاصة مقفلة في هذه الغرفة');
    if (user.isMuted) return fail(cb, 'أنت مكتوم');

    let cleanText = '';
    if (text && typeof text === 'string' && text.trim()) {
      const clean = filter.check(text);
      if (!clean.ok) return fail(cb, clean.reason);
      cleanText = clean.text;
    } else if (mediaType === 'image') {
      cleanText = '📷 صورة خاصة';
    } else if (mediaType === 'audio') {
      cleanText = '🎤 تسجيل صوتي خاص';
    } else {
      return fail(cb, 'رسالة فارغة');
    }

    let safeMediaUrl = null;
    let safeMediaType = null;
    let safeAudioDuration = null;
    if (typeof mediaUrl === 'string' && (mediaUrl.startsWith('/uploads/') || mediaUrl.startsWith('https://') || mediaUrl.startsWith('http://'))) {
      safeMediaUrl = mediaUrl.slice(0, 500);
      safeMediaType = mediaType === 'audio' ? 'audio' : 'image';
      if (safeMediaType === 'audio' && typeof audioDuration === 'number') {
        safeAudioDuration = Math.min(300, Math.max(1, Math.floor(audioDuration)));
      }
    }

    const msg = makeMessage(user, 'private', cleanText, {
      toUserId,
      mediaType: safeMediaType,
      mediaUrl: safeMediaUrl,
      audioDuration: safeAudioDuration
    });
    store.pushPrivate(user.id, toUserId, msg);
    const tp = online.get(toUserId);
    if (tp) io.to(tp.socketId).emit('private_message', msg);
    else pushToUser(target, `رسالة خاصة من ${user.name}`, cleanText, { type: 'private', fromUserId: user.id });
    socket.emit('private_message', msg);
    if (typeof cb === 'function') cb({ ok: true, message: msg });
  });

  socket.on('private_history', ({ withUserId } = {}, cb) => {
    const user = currentUser();
    if (!user) return fail(cb, 'سجل الدخول');
    const key = store.pairKey(user.id, withUserId);
    if (typeof cb === 'function') cb({ ok: true, messages: store.state.privates[key] || [] });
  });

  // Private-message inbox: list everyone this user has a thread with (condition 1 button).
  socket.on('list_threads', (_p, cb) => {
    const user = currentUser();
    if (!user) return fail(cb, 'سجل الدخول');
    const threads = [];
    for (const [key, list] of Object.entries(store.state.privates)) {
      const ids = key.split('|');
      if (!ids.includes(user.id) || !list.length) continue;
      const otherId = ids[0] === user.id ? ids[1] : ids[0];
      const other = store.state.users[otherId];
      if (!other) continue;
      const last = list[list.length - 1];
      threads.push({
        userId: other.id, name: other.name, rank: other.rank,
        avatarUrl: other.avatarUrl || '', lastText: last.text, at: last.at || 0
      });
    }
    threads.sort((a, b) => b.at - a.at);
    if (typeof cb === 'function') cb({ ok: true, threads });
  });

  // Friend / add requests (condition 1 requests button).
  socket.on('send_request', ({ toUserId } = {}, cb) => {
    const user = currentUser();
    if (!user) return fail(cb, 'سجل الدخول');
    const target = store.state.users[toUserId];
    if (!target || target.id === user.id) return fail(cb, 'العضو غير موجود');
    target.requests = target.requests || [];
    user.friends = user.friends || [];
    if (user.friends.includes(toUserId)) return fail(cb, 'أنتم أصدقاء بالفعل');
    if (!target.requests.includes(user.id)) target.requests.push(user.id);
    store.flush();
    const tp = online.get(toUserId);
    if (tp) io.to(tp.socketId).emit('request_received', { fromUserId: user.id, fromName: user.name });
    else pushToUser(target, 'طلب صداقة', `${user.name} يريد إضافتك`, { type: 'request', fromUserId: user.id });
    if (typeof cb === 'function') cb({ ok: true });
  });

  socket.on('list_requests', (_p, cb) => {
    const user = currentUser();
    if (!user) return fail(cb, 'سجل الدخول');
    const reqs = (user.requests || []).map(id => store.state.users[id]).filter(Boolean)
      .map(u => ({ userId: u.id, name: u.name, rank: u.rank, avatarUrl: u.avatarUrl || '' }));
    if (typeof cb === 'function') cb({ ok: true, requests: reqs });
  });

  socket.on('accept_request', ({ fromUserId, accept } = {}, cb) => {
    const user = currentUser();
    if (!user) return fail(cb, 'سجل الدخول');
    user.requests = (user.requests || []).filter(id => id !== fromUserId);
    if (accept !== false) {
      const other = store.state.users[fromUserId];
      if (other) {
        user.friends = user.friends || []; other.friends = other.friends || [];
        if (!user.friends.includes(fromUserId)) user.friends.push(fromUserId);
        if (!other.friends.includes(user.id)) other.friends.push(user.id);
      }
    }
    store.flush();
    if (typeof cb === 'function') cb({ ok: true });
  });

  // Scoreboard / leaderboard of quiz points (burger-menu button).
  socket.on('list_scores', (_p, cb) => {
    const arr = [];
    if (quizBot && quizBot.scores) {
      for (const [uid, score] of quizBot.scores.entries()) {
        const u = store.state.users[uid];
        if (u) arr.push({ userId: uid, name: u.displayName || u.name, rank: u.rank, avatarUrl: u.avatarUrl || '', score });
      }
    }
    arr.sort((a, b) => b.score - a.score);
    if (typeof cb === 'function') cb({ ok: true, scores: arr.slice(0, 50) });
  });

  socket.on('update_profile', ({ customHexColor, avatarUrl, status, bio, age, gender, country, displayName, lockPrivate, muteNotifications, isGhost } = {}, cb) => {
    const user = currentUser();
    if (!user) return fail(cb, 'سجل الدخول');
    if (typeof displayName === 'string' && displayName.trim().length >= 2) {
      const trimmed = displayName.trim().slice(0, 50);
      const current = user.displayName || user.name;
      if (trimmed !== current) {
        if (!user.oldNames) user.oldNames = [];
        if (!user.oldNames.includes(current)) user.oldNames.unshift(current);
        if (user.oldNames.length > 20) user.oldNames.pop();
        user.displayName = trimmed;
      }
    }
    if (typeof avatarUrl === 'string') {
      if (!isVip(user) && avatarUrl !== user.avatarUrl) {
        return fail(cb, 'تغيير الصورة الرمزية متاح لرتبة VIP فما فوق');
      }
      user.avatarUrl = avatarUrl.slice(0, 300);
    }
    if (typeof status === 'string' && ['online', 'away', 'busy'].includes(status)) user.status = status;
    if (typeof bio === 'string') user.bio = bio.slice(0, 160);
    if (age !== undefined) user.age = Number(age) || null;
    if (typeof gender === 'string') user.gender = gender.slice(0, 20);
    if (typeof country === 'string') user.country = country.slice(0, 50);
    if (typeof customHexColor === 'string' && /^#[0-9a-fA-F]{6}$/.test(customHexColor)) {
      if (!isVip(user)) {
        return fail(cb, 'تخصيص لون الاسم والفقاعة متاح لرتبة VIP فما فوق');
      }
      user.customHexColor = customHexColor;
    }
    if (lockPrivate !== undefined) user.lockPrivate = !!lockPrivate;
    if (muteNotifications !== undefined) user.muteNotifications = !!muteNotifications;
    if (isGhost !== undefined && isStaff(user)) user.isGhost = !!isGhost;

    store.flush();
    const p = online.get(user.id);
    if (p) io.to(p.roomId).emit('user_updated', { user: publicUser(user) });
    if (typeof cb === 'function') cb({ ok: true, user: publicUser(user) });
  });

  function moderate(action, payload, cb) {
    const me = currentUser();
    if (!isStaff(me)) return fail(cb, 'صلاحية مشرف مطلوبة');
    const needTarget = !['lock_room', 'broadcast', 'trigger_quiz', 'unban_device'].includes(action);
    const target = store.state.users[payload.targetUserId];
    if (needTarget && !target) return fail(cb, 'العضو غير موجود');
    if (target && rank(target) >= rank(me) && target.id !== me.id) return fail(cb, 'لا يمكن التحكم بعضو رتبته أعلى');

    const p = target && online.get(target.id);
    switch (action) {
      case 'mute':
        target.isMuted = true;
        store.logAdmin(me, 'mute', target, payload.reason || 'كتم العضو');
        break;
      case 'unmute':
        target.isMuted = false;
        store.logAdmin(me, 'unmute', target, 'إلغاء الكتم');
        break;
      case 'ghost':
        target.isGhost = true;
        store.logAdmin(me, 'ghost', target, 'تفعيل وضع الشبح');
        break;
      case 'unghost':
        target.isGhost = false;
        store.logAdmin(me, 'unghost', target, 'إلغاء وضع الشبح');
        break;
      case 'kick':
        if (p) { io.to(p.socketId).emit('force_disconnect', { reason: 'تم طردك من الغرفة' }); io.sockets.sockets.get(p.socketId)?.disconnect(true); }
        store.logAdmin(me, 'kick', target, payload.reason || 'طرد من الغرفة');
        break;
      case 'ban_device':
        for (const d of (target.devices || [])) store.state.bannedDevices[d] = { by: me.name, at: now() };
        if (p) { io.to(p.socketId).emit('force_disconnect', { reason: 'تم حظر جهازك نهائياً' }); io.sockets.sockets.get(p.socketId)?.disconnect(true); }
        store.logAdmin(me, 'ban_device', target, 'حظر الجهاز نهائياً');
        break;
      case 'unban_device':
        if (payload.deviceHash && store.state.bannedDevices[payload.deviceHash]) {
          delete store.state.bannedDevices[payload.deviceHash];
          store.logAdmin(me, 'unban_device', null, `إلغاء حظر جهاز: ${payload.deviceHash}`);
        }
        break;
      case 'delete_user':
        if (me.rank !== 'OWNER' && me.rank !== 'ADMIN') return fail(cb, 'حذف الحساب متاح للمدير والمالك فقط');
        if (p) {
          io.to(p.socketId).emit('force_disconnect', { reason: 'تم حذف حسابك نهائياً من قبل الإدارة' });
          io.sockets.sockets.get(p.socketId)?.disconnect(true);
        }
        store.logAdmin(me, 'delete_user', target, 'حذف الحساب نهائياً');
        delete store.state.users[target.id];
        delete store.state.names[(target.name || '').toLowerCase()];
        for (const [tok, uid] of Object.entries(store.state.tokens)) {
          if (uid === target.id) delete store.state.tokens[tok];
        }
        break;
      case 'trigger_quiz': {
        const room = store.state.rooms[(online.get(me.id) || {}).roomId] || store.state.rooms.iraq;
        quizBot.askQuestion(room.id);
        store.logAdmin(me, 'trigger_quiz', null, `تشغيل سؤال مسابقات في غرفة ${room.id}`);
        break;
      }
      case 'promote':
        if (me.rank !== 'OWNER' && (me.rank !== 'MODERATOR' || payload.rank !== 'VIP_DIAMOND')) return fail(cb, 'الترقية للمشرفين أو المالك فقط');
        if (RANKS.includes(payload.rank)) {
          const prevRank = target.rank;
          target.rank = payload.rank;
          store.logAdmin(me, 'promote', target, `ترقية من ${prevRank} إلى ${payload.rank}`);
        }
        break;
      case 'lock_room': {
        const room = store.state.rooms[(online.get(me.id) || {}).roomId];
        if (!room) return fail(cb, 'لست داخل غرفة');
        if (payload.what === 'public') room.lockPublic = !!payload.value;
        if (payload.what === 'private') room.lockPrivate = !!payload.value;
        store.logAdmin(me, 'lock_room', null, `تعديل قفل الغرفة ${room.id}: ${payload.what}=${payload.value}`);
        io.to(room.id).emit('room_updated', { id: room.id, lockPublic: !!room.lockPublic, lockPrivate: !!room.lockPrivate });
        break;
      }
      case 'broadcast': {
        const text = String(payload.text || '').slice(0, 300);
        store.logAdmin(me, 'broadcast', null, `إرسال تنبيه عام: ${text}`);
        io.emit('broadcast', { text, by: me.name, at: now() });
        if (push.enabled()) {
          for (const u of Object.values(store.state.users)) {
            if (!online.has(u.id) && u.pushTokens && u.pushTokens.length) pushToUser(u, 'إشعار عام', text, { type: 'broadcast' });
          }
        }
        break;
      }
      default: return fail(cb, 'إجراء غير معروف');
    }
    store.flush();
    if (target && action !== 'delete_user') {
      const tp = online.get(target.id);
      if (tp) io.to(tp.roomId).emit('user_updated', { user: publicUser(target) });
      // Refresh the online list everywhere the mod and target are, so the mute
      // icon / rank / removal shows live (this is what made mute look "not working").
      const mp = online.get(me.id);
      if (mp && mp.roomId) broadcastUserList(mp.roomId);
      if (tp && tp.roomId && (!mp || tp.roomId !== mp.roomId)) broadcastUserList(tp.roomId);
    } else if (action === 'delete_user') {
      for (const r of Object.keys(store.state.rooms)) broadcastUserList(r);
    }
    const done = { mute: 'تم كتم العضو', unmute: 'تم فك الكتم', kick: 'تم طرد العضو', ban_device: 'تم حظر الجهاز نهائياً', ghost: 'تم تفعيل وضع الشبح', unghost: 'تم إلغاء وضع الشبح', promote: 'تم تغيير الرتبة', delete_user: 'تم حذف الحساب نهائياً' };
    if (done[action]) socket.emit('error_alert', { message: '✅ ' + done[action] });
    if (typeof cb === 'function') cb({ ok: true });
  }

  socket.on('mod_action', ({ action, ...payload } = {}, cb) => moderate(action, payload, cb));

  socket.on('user_inspect', ({ targetUserId } = {}, cb) => {
    const me = currentUser();
    if (!isStaff(me)) return fail(cb, 'صلاحية مشرف مطلوبة');
    const target = store.state.users[targetUserId];
    if (!target) return fail(cb, 'العضو غير موجود');
    const linked = getLinkedAccounts(target);
    if (typeof cb === 'function') {
      cb({
        ok: true,
        user: publicUser(target),
        oldNames: target.oldNames || [],
        linkedAccounts: linked,
        devices: target.devices || [],
        lastIp: target.lastIp || '',
        createdAt: target.createdAt || null
      });
    }
  });

  socket.on('list_banned', (_p, cb) => {
    const me = currentUser();
    if (!isStaff(me)) return fail(cb, 'صلاحية مشرف مطلوبة');
    const banned = Object.entries(store.state.bannedDevices).map(([hash, info]) => ({ hash, ...info }));
    if (typeof cb === 'function') cb({ ok: true, banned });
  });

  socket.on('list_staff', (_p, cb) => {
    const me = currentUser();
    if (!isStaff(me)) return fail(cb, 'صلاحية مشرف مطلوبة');
    const staff = Object.values(store.state.users).filter(u => isStaff(u)).map(publicUser);
    if (typeof cb === 'function') cb({ ok: true, staff });
  });

  socket.on('device_accounts', ({ targetUserId } = {}, cb) => {
    const me = currentUser();
    if (!isStaff(me)) return fail(cb, 'صلاحية مشرف مطلوبة');
    const target = store.state.users[targetUserId];
    if (!target) return fail(cb, 'العضو غير موجود');
    const alts = new Set();
    for (const d of (target.devices || [])) for (const uid of (store.state.deviceAccounts[d] || [])) alts.add(uid);
    const list = [...alts].map(uid => store.state.users[uid]).filter(Boolean).map(publicUser);
    if (typeof cb === 'function') cb({ ok: true, accounts: list, oldNames: target.oldNames || [] });
  });

  socket.on('change_name', ({ newName } = {}, cb) => {
    const user = currentUser();
    if (!user) return fail(cb, 'سجل الدخول');
    if (!auth.validName(newName)) return fail(cb, 'اسم غير صالح');
    const key = newName.trim().toLowerCase();
    if (store.state.names[key]) return fail(cb, 'الاسم مستخدم');
    (user.oldNames || (user.oldNames = [])).push(user.name);
    delete store.state.names[user.name.toLowerCase()];
    user.name = newName.trim();
    store.state.names[key] = user.id;
    store.flush();
    const p = online.get(user.id);
    if (p) io.to(p.roomId).emit('user_updated', { user: publicUser(user) });
    if (typeof cb === 'function') cb({ ok: true, user: publicUser(user) });
  });

  socket.on('register_push', ({ pushToken } = {}, cb) => {
    const user = currentUser();
    if (!user || typeof pushToken !== 'string' || pushToken.length < 10) { if (typeof cb === 'function') cb({ ok: false }); return; }
    user.pushTokens = user.pushTokens || [];
    if (!user.pushTokens.includes(pushToken)) { user.pushTokens.push(pushToken); store.flush(); }
    if (typeof cb === 'function') cb({ ok: true });
  });

  socket.on('sync_youtube', ({ videoId, videoTitle, status } = {}, cb) => {
    const user = currentUser();
    const p = user && online.get(user.id);
    if (!p) { if (typeof cb === 'function') cb({ ok: false }); return; }
    const room = store.state.rooms[p.roomId];
    if (room && videoId) {
      room.youtubeId = videoId;
      if (videoTitle) room.youtubeTitle = videoTitle;
      room.youtubeStartedAt = now();
      room.youtubeStartedBy = user.name;
      room.isWelcome = false;
      store.save();
    }
    io.to(p.roomId).emit('youtube_updated', {
      videoId,
      videoTitle: videoTitle || room?.youtubeTitle || 'يوتيوب مشترك',
      startedAt: room?.youtubeStartedAt || now(),
      startedBy: user.name,
      offset: 0,
      status: status || 'play',
      by: user.name,
      isWelcome: false
    });
    if (typeof cb === 'function') cb({ ok: true });
  });

  socket.on('video_finished', ({ videoId } = {}, cb) => {
    const user = currentUser();
    const p = user && online.get(user.id);
    if (!p) return;
    const room = store.state.rooms[p.roomId];
    if (!room || !room.youtubeId) return;

    // Advance queue or fallback to welcome video or stop player cleanly
    const q = room.youtubeQueue || [];
    if (q.length > 0) {
      const nextItem = q.shift();
      room.youtubeId = nextItem.videoId;
      room.youtubeTitle = nextItem.title || ('فيديو بواسطة ' + nextItem.by);
      room.youtubeStartedAt = now();
      room.youtubeStartedBy = nextItem.by;
      room.isWelcome = false;
      store.save();
      io.to(room.id).emit('youtube_updated', {
        videoId: nextItem.videoId,
        videoTitle: room.youtubeTitle,
        startedAt: room.youtubeStartedAt,
        startedBy: nextItem.by,
        offset: 0,
        status: 'play',
        by: nextItem.by,
        isWelcome: false
      });
      io.to(room.id).emit('system_message', {
        roomId: room.id,
        text: `🎬 بدأ تلقائياً الفيديو التالي في قائمة الانتظار: ${room.youtubeTitle} (بواسطة ${nextItem.by}) 🎵`,
        at: now()
      });
    } else if (room.welcomeVideoId) {
      room.youtubeId = room.welcomeVideoId;
      room.youtubeTitle = room.welcomeVideoTitle || ('فيديو ترحيبي - ' + room.title);
      room.youtubeStartedAt = now();
      room.youtubeStartedBy = 'فيديو ترحيبي';
      room.isWelcome = true;
      store.save();
      io.to(room.id).emit('youtube_updated', {
        videoId: room.welcomeVideoId,
        videoTitle: room.youtubeTitle,
        startedAt: room.youtubeStartedAt,
        startedBy: 'فيديو ترحيبي',
        offset: 0,
        status: 'play',
        by: 'نظام الغرفة',
        isWelcome: true
      });
      io.to(room.id).emit('system_message', {
        roomId: room.id,
        text: `🎬 تم العودة للفيديو الترحيبي للغرفة 🌟`,
        at: now()
      });
    } else {
      room.youtubeId = '';
      room.youtubeTitle = '';
      room.youtubeStartedAt = 0;
      room.youtubeStartedBy = '';
      room.isWelcome = false;
      store.save();
      io.to(room.id).emit('youtube_updated', {
        videoId: '',
        videoTitle: '',
        startedAt: 0,
        startedBy: '',
        offset: 0,
        status: 'ended',
        by: 'system',
        isWelcome: false
      });
      io.to(room.id).emit('system_message', {
        roomId: room.id,
        text: `🏁 اكتمل عرض الفيديو وتوقف المشغل بنجاح. أرسل /yt أو /q لتشغيل فيديو جديد 🎵`,
        at: now()
      });
    }
    if (typeof cb === 'function') cb({ ok: true });
  });

  socket.on('disconnect', () => {
    clearTimeout(authTimer);
    if (socket.clientIp) {
      const activeForIp = ipSockets.get(socket.clientIp);
      if (activeForIp) {
        activeForIp.delete(socket.id);
        if (activeForIp.size === 0) ipSockets.delete(socket.clientIp);
      }
    }
    const uid = socket.data.userId;
    if (uid && online.get(uid)?.socketId === socket.id) {
      const roomId = online.get(uid).roomId;
      online.delete(uid);
      broadcastUserList(roomId);
      io.emit('rooms', { rooms: roomsSummary() });
    }
  });
});

app.get('/health', (_req, res) => {
  res.json({
    status: 'ok',
    uptime: process.uptime(),
    users: Object.keys(store.state.users).length,
    online: online.size,
    rooms: Object.keys(store.state.rooms).length,
    bannedDevices: Object.keys(store.state.bannedDevices).length
  });
});

server.listen(PORT, () => console.log(`🎧 Iraqi chat server on port ${PORT}`));

module.exports = { app, server, io };
