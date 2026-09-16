'use strict';
/**
 * High-performance real-time chat server for the Arabic / Iraqi rooms app.
 * Socket.io + JSON-file store (lib/store.js). Covers Ali's 9 requirements:
 * accounts, ranks, anti-spam, auto-mute, moderation, filter, ghost mode,
 * strict device ban, in-chat YouTube sync, permanent logs.
 */
const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const cors = require('cors');

const store = require('./lib/store');
const auth = require('./lib/auth');
const filter = require('./lib/filter');
const push = require('./lib/push');
const { QuizBot } = require('./lib/bot');

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

function publicUser(u) {
  return {
    id: u.id, name: u.name, avatarUrl: u.avatarUrl || '', rank: u.rank,
    customHexColor: u.customHexColor || null, country: u.country || 'IQ',
    isMuted: !!u.isMuted, isGhost: !!u.isGhost, status: u.status || 'online',
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
  if (roomId === 'games' || roomId === 'iraq') {
    out.push(quizBot.user);
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
    senderName: u.name,
    senderAvatar: u.avatarUrl || '',
    senderRank: u.rank,
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

io.use((socket, next) => {
  const deviceHash = socket.handshake.query.deviceHash || '';
  socket.deviceHash = deviceHash;
  if (deviceHash && store.state.bannedDevices[deviceHash]) {
    return next(new Error('DEVICE_BANNED: تم حظر عتاد هذا الجهاز نهائياً'));
  }
  next();
});

io.on('connection', (socket) => {
  socket.data.userId = null;

  const currentUser = () => (socket.data.userId ? store.state.users[socket.data.userId] : null);
  const fail = (cb, message) => { if (typeof cb === 'function') cb({ ok: false, error: message }); };

  socket.on('register', ({ name, password, avatarUrl } = {}, cb) => {
    if (!auth.validName(name)) return fail(cb, 'الاسم يجب أن يكون بين 2 و20 حرفاً');
    if (typeof password !== 'string' || password.length < 3) return fail(cb, 'الرمز السري قصير جداً');
    const key = name.trim().toLowerCase();
    if (store.state.names[key]) return fail(cb, 'الاسم مستخدم مسبقاً');

    const { salt, hash } = auth.hashPassword(password);
    const id = store.nextId('u');
    const isFirst = Object.keys(store.state.users).length === 0;
    const user = {
      id, name: name.trim(), salt, hash,
      avatarUrl: avatarUrl || '',
      rank: isFirst ? 'OWNER' : 'REGULAR',
      customHexColor: null, country: 'IQ',
      isMuted: process.env.AUTO_MUTE === 'true' ? !isFirst : false,
      isGhost: false, status: 'online',
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
          youtubeTitle: room.youtubeTitle || ''
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
    if (!p) return;
    const room = store.state.rooms[p.roomId];
    if (!room) return;

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
      room.youtubeTitle = 'فيديو من ' + user.name;
      store.save();
      io.to(room.id).emit('youtube_updated', { videoId: yt, videoTitle: room.youtubeTitle, status: 'play', by: user.name });
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

  socket.on('update_profile', ({ customHexColor, avatarUrl, status } = {}, cb) => {
    const user = currentUser();
    if (!user) return fail(cb, 'سجل الدخول');
    if (typeof avatarUrl === 'string') user.avatarUrl = avatarUrl.slice(0, 300);
    if (typeof status === 'string' && ['online', 'away', 'busy'].includes(status)) user.status = status;
    if (typeof customHexColor === 'string' && /^#[0-9a-fA-F]{6}$/.test(customHexColor)) {
      if (rank(user) >= RANKS.indexOf('VIP_DIAMOND')) user.customHexColor = customHexColor;
      else return fail(cb, 'تغيير لون الاسم متاح للأعضاء المميزين فقط');
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
      store.save();
    }
    io.to(p.roomId).emit('youtube_updated', {
      videoId,
      videoTitle: videoTitle || room?.youtubeTitle || 'يوتيوب مشترك',
      status: status || 'play',
      by: user.name
    });
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
