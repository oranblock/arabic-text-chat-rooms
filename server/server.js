'use strict';
/**
 * High-performance real-time chat server for the Arabic / Iraqi rooms app.
 * Socket.io + JSON-file store (lib/store.js). Covers Ali's 9 requirements:
 * accounts, ranks, anti-spam, auto-mute, moderation, filter, ghost mode,
 * strict device ban, in-chat YouTube sync, permanent logs.
 */
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
app.use(cors());
app.use(express.json());
const server = http.createServer(app);
const io = new Server(server, { cors: { origin: '*', methods: ['GET', 'POST'] } });
const PORT = process.env.PORT || 3000;

const RANKS = ['REGULAR', 'BOT', 'VIP_DIAMOND', 'MODERATOR', 'OWNER'];
const rank = (u) => (u ? RANKS.indexOf(u.rank) : -1);
const isStaff = (u) => u && (u.rank === 'MODERATOR' || u.rank === 'OWNER');
const now = () => Date.now();
const hhmm = () => new Date().toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });

const online = new Map();       // userId -> { socketId, roomId }
const lastSent = new Map();     // userId -> { at, text }
const registerAttempts = new Map(); // (deviceHash or IP) -> [timestamp]

function publicUser(u) {
  return {
    id: u.id, name: u.displayName || u.name, loginName: u.name, avatarUrl: u.avatarUrl || '', rank: u.rank,
    customHexColor: u.customHexColor || null, country: u.country || 'العراق',
    bio: u.bio || '', age: u.age || null, gender: u.gender || '',
    isMuted: !!u.isMuted, isGhost: !!u.isGhost, status: u.status || 'online',
    isGuest: !!u.isGuest, guestExpiresAt: u.guestExpiresAt || null,
    online: online.has(u.id)
  };
}

function roomUsers(roomId) {
  const out = [];
  for (const [uid, p] of online) {
    if (p.roomId === roomId) {
      const u = store.state.users[uid];
      if (u) out.push(publicUser(u));
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

// Purge expired guest accounts (temporary, deleted 1 hour after creation).
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
}, 5 * 60 * 1000);

const adminRouter = createAdminRouter({
  store, auth, online, io, quizBot, publicUser, roomsSummary, isStaff, rank, RANKS, now, makeMessage
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
  const deviceHash = socket.handshake.query.deviceHash || '';
  socket.deviceHash = deviceHash;
  if (deviceHash && store.state.bannedDevices[deviceHash]) {
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

  const currentUser = () => (socket.data.userId ? store.state.users[socket.data.userId] : null);
  const fail = (cb, message) => { if (typeof cb === 'function') cb({ ok: false, error: message }); };

  socket.on('register', ({ name, password, avatarUrl, guest } = {}, cb) => {
    const clientKey = socket.deviceHash || socket.handshake.address || 'unknown';
    const nowTime = now();
    const attempts = (registerAttempts.get(clientKey) || []).filter(t => nowTime - t < 60000);
    if (attempts.length >= 3) {
      return fail(cb, 'تم تجاوز حد محاولات التسجيل السريعة. يرجى الانتظار دقيقة.');
    }
    attempts.push(nowTime);
    registerAttempts.set(clientKey, attempts);

    if (socket.deviceHash) {
      const existing = store.state.deviceAccounts[socket.deviceHash] || [];
      if (existing.length >= 5) {
        return fail(cb, 'تم تجاوز الحد الأقصى للحسابات المسموح بإنشائها من هذا الجهاز (5 حسابات كحد أقصى)');
      }
    }

    if (!auth.validName(name)) return fail(cb, 'الاسم يجب أن يكون بين 2 و50 حرفاً');
    if (typeof password !== 'string' || password.length < 3) return fail(cb, 'الرمز السري قصير جداً');
    const key = name.trim().toLowerCase();
    if (store.state.names[key]) return fail(cb, 'الاسم مستخدم مسبقاً');

    const { salt, hash } = auth.hashPassword(password);
    const id = store.nextId('u');
    const isFirst = Object.keys(store.state.users).length === 0;
    const isGuest = !!guest && !isFirst;
    // New members get a random pleasant color automatically (not a fixed one).
    const AUTO_COLORS = ['#f3d5d5', '#e9f4d4', '#d5eef5', '#e9dcee', '#f3e6d4', '#fad5f6', '#ece9ff', '#FD62BE'];
    const user = {
      id, name: name.trim(), salt, hash,
      avatarUrl: avatarUrl || '',
      rank: isFirst ? 'OWNER' : 'REGULAR',
      customHexColor: isFirst ? null : AUTO_COLORS[Math.floor(Math.random() * AUTO_COLORS.length)], country: 'IQ',
      isMuted: process.env.AUTO_MUTE === 'true' ? !isFirst : false,
      isGhost: false, status: 'online',
      isGuest, guestExpiresAt: isGuest ? now() + 3600000 : null,
      oldNames: [], createdAt: now(),
      devices: socket.deviceHash ? [socket.deviceHash] : []
    };
    store.state.users[id] = user;
    store.state.names[key] = id;
    if (socket.deviceHash) {
      const list = store.state.deviceAccounts[socket.deviceHash] || (store.state.deviceAccounts[socket.deviceHash] = []);
      if (!list.includes(id)) list.push(id);
    }
    const token = auth.newToken();
    store.state.tokens[token] = id;
    store.flush();
    if (typeof cb === 'function') cb({ ok: true, token, user: publicUser(user) });
  });

  socket.on('login', ({ name, password, token } = {}, cb) => {
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
    if (socket.deviceHash && !(user.devices || []).includes(socket.deviceHash)) {
      (user.devices || (user.devices = [])).push(socket.deviceHash);
      const list = store.state.deviceAccounts[socket.deviceHash] || (store.state.deviceAccounts[socket.deviceHash] = []);
      if (!list.includes(user.id)) list.push(user.id);
    }
    const newTok = token && store.state.tokens[token] ? token : auth.newToken();
    store.state.tokens[newTok] = user.id;
    store.flush();
    if (typeof cb === 'function') cb({ ok: true, token: newTok, user: publicUser(user) });
  });

  socket.on('join_room', ({ token, roomId } = {}, cb) => {
    const uid = token && store.state.tokens[token];
    const user = uid && store.state.users[uid];
    if (!user) return fail(cb, 'الجلسة منتهية، سجل الدخول من جديد');
    const room = store.state.rooms[roomId] || store.state.rooms.iraq;

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
    io.to(room.id).emit('system_message', { roomId: room.id, text: `${user.name} دخل الغرفة`, at: now() });
    broadcastUserList(room.id);
    io.emit('rooms', { rooms: roomsSummary() });
  });

  socket.on('list_rooms', (_p, cb) => { if (typeof cb === 'function') cb({ ok: true, rooms: roomsSummary() }); });

  socket.on('send_message', ({ text } = {}) => {
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

    const clean = filter.check(text);
    if (!clean.ok) return socket.emit('error_alert', { message: clean.reason });

    const last = lastSent.get(user.id);
    if (last && !isStaff(user)) {
      if (now() - last.at < 800) {
        user.isMuted = true; store.flush();
        io.to(room.id).emit('user_updated', { user: publicUser(user) });
        return socket.emit('error_alert', { message: 'تم كتمك تلقائياً بسبب الإغراق' });
      }
      if (now() - last.at < 2000) return socket.emit('error_alert', { message: 'يرجى الانتظار ثانيتين بين كل رسالة' });
      if (last.text === clean.text) return socket.emit('error_alert', { message: 'لا تكرر نفس الرسالة' });
    }
    lastSent.set(user.id, { at: now(), text: clean.text });

    const msg = makeMessage(user, room.id, clean.text);
    if (user.isGhost) {
      // Silent shadowban: echo the sender's own message back looking fully delivered
      // (never stored, never broadcast). The target must not know they are ghosted,
      // so we send it plain — no isGhost flag — to keep them calm instead of raging.
      return socket.emit('new_message', msg);
    }
    store.pushMessage(room.id, msg);
    io.to(room.id).emit('new_message', msg);
    quizBot.checkAnswer(room.id, user, clean.text);

    const yt = filter.youtubeId(clean.text);
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

  socket.on('private_send', ({ toUserId, text } = {}, cb) => {
    const user = currentUser();
    if (!user) return fail(cb, 'سجل الدخول');
    const target = store.state.users[toUserId];
    if (!target) return fail(cb, 'العضو غير موجود');
    const room = store.state.rooms[(online.get(user.id) || {}).roomId];
    if (room && room.lockPrivate && !isStaff(user)) return fail(cb, 'المحادثات الخاصة مقفلة');
    if (user.isMuted) return fail(cb, 'أنت مكتوم');
    const clean = filter.check(text);
    if (!clean.ok) return fail(cb, clean.reason);

    const msg = makeMessage(user, 'private', clean.text, { toUserId });
    store.pushPrivate(user.id, toUserId, msg);
    const tp = online.get(toUserId);
    if (tp) io.to(tp.socketId).emit('private_message', msg);
    else pushToUser(target, `رسالة خاصة من ${user.name}`, clean.text, { type: 'private', fromUserId: user.id });
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

  socket.on('update_profile', ({ customHexColor, avatarUrl, status, bio, age, gender, country, displayName } = {}, cb) => {
    const user = currentUser();
    if (!user) return fail(cb, 'سجل الدخول');
    if (typeof displayName === 'string' && displayName.trim().length >= 2) user.displayName = displayName.trim().slice(0, 50);
    if (typeof avatarUrl === 'string') user.avatarUrl = avatarUrl.slice(0, 300);
    if (typeof status === 'string' && ['online', 'away', 'busy'].includes(status)) user.status = status;
    if (typeof bio === 'string') user.bio = bio.slice(0, 160);
    if (age !== undefined) user.age = Number(age) || null;
    if (typeof gender === 'string') user.gender = gender.slice(0, 20);
    if (typeof country === 'string') user.country = country.slice(0, 50);
    if (typeof customHexColor === 'string' && /^#[0-9a-fA-F]{6}$/.test(customHexColor)) {
      user.customHexColor = customHexColor;  // everyone may pick a color
    }
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
      case 'mute': target.isMuted = true; break;
      case 'unmute': target.isMuted = false; break;
      case 'ghost': target.isGhost = true; break;
      case 'unghost': target.isGhost = false; break;
      case 'kick':
        if (p) { io.to(p.socketId).emit('force_disconnect', { reason: 'تم طردك من الغرفة' }); io.sockets.sockets.get(p.socketId)?.disconnect(true); }
        break;
      case 'ban_device':
        for (const d of (target.devices || [])) store.state.bannedDevices[d] = { by: me.name, at: now() };
        if (p) { io.to(p.socketId).emit('force_disconnect', { reason: 'تم حظر جهازك نهائياً' }); io.sockets.sockets.get(p.socketId)?.disconnect(true); }
        break;
      case 'unban_device':
        if (payload.deviceHash && store.state.bannedDevices[payload.deviceHash]) {
          delete store.state.bannedDevices[payload.deviceHash];
        }
        break;
      case 'trigger_quiz': {
        const room = store.state.rooms[(online.get(me.id) || {}).roomId] || store.state.rooms.iraq;
        quizBot.askQuestion(room.id);
        break;
      }
      case 'promote':
        if (me.rank !== 'OWNER' && (me.rank !== 'MODERATOR' || payload.rank !== 'VIP_DIAMOND')) return fail(cb, 'الترقية للمشرفين أو المالك فقط');
        if (RANKS.includes(payload.rank)) target.rank = payload.rank;
        break;
      case 'lock_room': {
        const room = store.state.rooms[(online.get(me.id) || {}).roomId];
        if (!room) return fail(cb, 'لست داخل غرفة');
        if (payload.what === 'public') room.lockPublic = !!payload.value;
        if (payload.what === 'private') room.lockPrivate = !!payload.value;
        io.to(room.id).emit('room_updated', { id: room.id, lockPublic: !!room.lockPublic, lockPrivate: !!room.lockPrivate });
        break;
      }
      case 'broadcast': {
        const text = String(payload.text || '').slice(0, 300);
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
    if (target) {
      const tp = online.get(target.id);
      if (tp) io.to(tp.roomId).emit('user_updated', { user: publicUser(target) });
    }
    if (typeof cb === 'function') cb({ ok: true });
  }

  socket.on('mod_action', ({ action, ...payload } = {}, cb) => moderate(action, payload, cb));

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
