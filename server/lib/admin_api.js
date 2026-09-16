'use strict';
/**
 * Standalone Web Admin API & Management Router.
 * Powers the web dashboard at /admin to manage multiple users simultaneously,
 * view live metrics, trigger bot events, and control rooms.
 */

const express = require('express');

function createAdminRouter({ store, auth, online, io, quizBot, publicUser, roomsSummary, isStaff, rank, RANKS, now, makeMessage }) {
  const router = express.Router();

  // Middleware to authenticate staff tokens (Bearer header or query ?token=)
  function requireStaff(req, res, next) {
    const authHeader = req.headers.authorization || '';
    const token = authHeader.replace(/^Bearer\s+/i, '') || req.query.token || req.body?.token;
    if (!token || !store.state.tokens[token]) {
      return res.status(401).json({ ok: false, error: 'غير مصرح: يلزم تسجيل الدخول كإداري' });
    }
    const uid = store.state.tokens[token];
    const user = store.state.users[uid];
    if (!user || !isStaff(user)) {
      return res.status(403).json({ ok: false, error: 'ممنوع: صلاحيات الإشراف مطلوبة' });
    }
    req.adminUser = user;
    next();
  }

  // Admin login endpoint
  router.post('/login', (req, res) => {
    const { name, password } = req.body || {};
    if (!name || !password) return res.status(400).json({ ok: false, error: 'يرجى إدخال الاسم وكلمة المرور' });

    const key = name.trim().toLowerCase();
    const id = store.state.names[key];
    const user = id && store.state.users[id];

    if (!user || !auth.verifyPassword(password, user.salt, user.hash)) {
      return res.status(401).json({ ok: false, error: 'بيانات الدخول غير صحيحة' });
    }
    if (!isStaff(user)) {
      return res.status(403).json({ ok: false, error: 'هذا الحساب ليس لديه صلاحيات الإدارة' });
    }

    const token = auth.newToken();
    store.state.tokens[token] = user.id;
    store.flush();

    res.json({ ok: true, token, user: publicUser(user) });
  });

  // Verify token
  router.get('/auth-check', requireStaff, (req, res) => {
    res.json({ ok: true, user: publicUser(req.adminUser) });
  });

  // Comprehensive Admin Dashboard Data
  router.get('/data', requireStaff, (req, res) => {
    const allUsers = Object.values(store.state.users).map(u => {
      const p = online.get(u.id);
      return {
        id: u.id,
        name: u.name,
        rank: u.rank,
        customHexColor: u.customHexColor || null,
        country: u.country || 'IQ',
        isMuted: !!u.isMuted,
        isGhost: !!u.isGhost,
        status: u.status || 'online',
        online: !!p,
        currentRoom: p ? p.roomId : null,
        devicesCount: (u.devices || []).length,
        devices: u.devices || [],
        createdAt: u.createdAt || 0,
        quizScore: quizBot.scores.get(u.id) || 0
      };
    });

    const bannedList = Object.entries(store.state.bannedDevices).map(([hash, info]) => ({
      hash,
      by: info.by || 'النظام',
      at: info.at || 0
    }));

    // Leaderboard
    const scores = [];
    for (const [uid, score] of quizBot.scores.entries()) {
      const u = store.state.users[uid];
      if (u) scores.push({ userId: uid, name: u.name, rank: u.rank, score });
    }
    scores.sort((a, b) => b.score - a.score);

    // Recent messages across all rooms
    const recentMessages = [];
    for (const [rId, msgs] of Object.entries(store.state.messages)) {
      const slice = msgs.slice(-20);
      for (const m of slice) recentMessages.push(m);
    }
    recentMessages.sort((a, b) => (b.at || 0) - (a.at || 0));

    res.json({
      ok: true,
      stats: {
        totalUsers: allUsers.length,
        onlineCount: online.size,
        roomsCount: Object.keys(store.state.rooms).length,
        bannedCount: bannedList.length,
        botActive: !!quizBot.timer
      },
      users: allUsers,
      rooms: roomsSummary(),
      banned: bannedList,
      scores: scores.slice(0, 10),
      recentMessages: recentMessages.slice(0, 40)
    });
  });

  // Bulk actions on multiple users simultaneously
  router.post('/bulk-action', requireStaff, (req, res) => {
    const { action, userIds, newRank } = req.body || {};
    if (!Array.isArray(userIds) || userIds.length === 0) {
      return res.status(400).json({ ok: false, error: 'لم يتم تحديد أي مستخدم' });
    }

    const me = req.adminUser;
    let modified = 0;

    for (const uid of userIds) {
      const target = store.state.users[uid];
      if (!target) continue;
      // Staff cannot modify equal or higher rank unless owner
      if (rank(target) >= rank(me) && target.id !== me.id) continue;

      const p = online.get(target.id);

      switch (action) {
        case 'mute':
          target.isMuted = true;
          modified++;
          break;
        case 'unmute':
          target.isMuted = false;
          modified++;
          break;
        case 'ghost':
          target.isGhost = true;
          modified++;
          break;
        case 'unghost':
          target.isGhost = false;
          modified++;
          break;
        case 'kick':
          if (p) {
            io.to(p.socketId).emit('force_disconnect', { reason: 'تم طردك بواسطة إدارة النظام' });
            io.sockets.sockets.get(p.socketId)?.disconnect(true);
            modified++;
          }
          break;
        case 'promote':
          if (RANKS.includes(newRank)) {
            if (newRank === 'MODERATOR' && me.rank !== 'OWNER') continue;
            target.rank = newRank;
            modified++;
          }
          break;
      }

      if (p) {
        io.to(p.roomId).emit('user_updated', { user: publicUser(target) });
      }
    }

    store.flush();
    res.json({ ok: true, modified, total: userIds.length });
  });

  // Single user action
  router.post('/user-action', requireStaff, (req, res) => {
    const { action, targetUserId, newRank, deviceHash } = req.body || {};
    const me = req.adminUser;

    if (action === 'unban_device') {
      if (deviceHash && store.state.bannedDevices[deviceHash]) {
        delete store.state.bannedDevices[deviceHash];
        store.flush();
        return res.json({ ok: true });
      }
      return res.status(404).json({ ok: false, error: 'الجهاز غير موجود في قائمة الحظر' });
    }

    const target = store.state.users[targetUserId];
    if (!target) return res.status(404).json({ ok: false, error: 'المستخدم غير موجود' });
    if (rank(target) >= rank(me) && target.id !== me.id) {
      return res.status(403).json({ ok: false, error: 'لا يمكنك التحكم بمستخدم مساوٍ أو أعلى منك رتبة' });
    }

    const p = online.get(target.id);
    switch (action) {
      case 'mute': target.isMuted = true; break;
      case 'unmute': target.isMuted = false; break;
      case 'ghost': target.isGhost = true; break;
      case 'unghost': target.isGhost = false; break;
      case 'kick':
        if (p) {
          io.to(p.socketId).emit('force_disconnect', { reason: 'تم طردك بواسطة الإدارة' });
          io.sockets.sockets.get(p.socketId)?.disconnect(true);
        }
        break;
      case 'ban_device':
        for (const d of (target.devices || [])) store.state.bannedDevices[d] = { by: me.name, at: now() };
        if (p) {
          io.to(p.socketId).emit('force_disconnect', { reason: 'تم حظر جهازك نهائياً من قبل الإدارة' });
          io.sockets.sockets.get(p.socketId)?.disconnect(true);
        }
        break;
      case 'promote':
        if (newRank === 'MODERATOR' && me.rank !== 'OWNER') return res.status(403).json({ ok: false, error: 'ترقية المشرفين للمالك فقط' });
        if (RANKS.includes(newRank)) target.rank = newRank;
        break;
      default:
        return res.status(400).json({ ok: false, error: 'إجراء غير معروف' });
    }

    store.flush();
    if (p) io.to(p.roomId).emit('user_updated', { user: publicUser(target) });
    res.json({ ok: true, user: publicUser(target) });
  });

  // Room control
  router.post('/room-control', requireStaff, (req, res) => {
    const { roomId, lockPublic, lockPrivate, topic, youtubeId } = req.body || {};
    const room = store.state.rooms[roomId];
    if (!room) return res.status(404).json({ ok: false, error: 'الغرفة غير موجودة' });

    if (typeof lockPublic === 'boolean') room.lockPublic = lockPublic;
    if (typeof lockPrivate === 'boolean') room.lockPrivate = lockPrivate;
    if (typeof topic === 'string') room.topic = topic.slice(0, 100);
    if (typeof youtubeId === 'string') {
      room.youtubeId = youtubeId.trim();
      io.to(room.id).emit('youtube_updated', { videoId: room.youtubeId, videoTitle: room.topic || 'يوتيوب الغرفة', status: 'play', by: req.adminUser.name });
    }

    store.save();
    io.to(room.id).emit('room_updated', {
      id: room.id,
      lockPublic: !!room.lockPublic,
      lockPrivate: !!room.lockPrivate,
      topic: room.topic,
      youtubeId: room.youtubeId
    });
    io.emit('rooms', { rooms: roomsSummary() });

    res.json({ ok: true, room });
  });

  // Broadcast
  router.post('/broadcast', requireStaff, (req, res) => {
    const { text } = req.body || {};
    if (!text || !text.trim()) return res.status(400).json({ ok: false, error: 'الرجاء كتابة نص الإعلان' });

    const clean = text.trim().slice(0, 300);
    io.emit('broadcast', { text: clean, by: req.adminUser.name, at: now() });
    res.json({ ok: true });
  });

  // Quiz Bot Trigger
  router.post('/quiz-trigger', requireStaff, (req, res) => {
    const { roomId } = req.body || {};
    const targetRoom = roomId || 'games';
    const q = quizBot.askQuestion(targetRoom);
    res.json({ ok: true, question: q });
  });

  // CLI Command Execution from Dashboard Terminal
  router.post('/exec-command', requireStaff, (req, res) => {
    const { command, roomId } = req.body || {};
    if (!command || typeof command !== 'string') return res.status(400).json({ ok: false, error: 'أمر فارغ' });

    const room = store.state.rooms[roomId] || store.state.rooms.iraq;
    const mockSocket = {
      emit: (evt, data) => {}
    };

    let resultMsg = '';
    const fakeSocket = {
      emit: (evt, data) => {
        if (evt === 'system_message') resultMsg += `[نظام]: ${data.text}\n`;
        if (evt === 'error_alert') resultMsg += `[تنبيه]: ${data.message}\n`;
      }
    };

    const handled = handleAdminCommand(fakeSocket, req.adminUser, room, command, {
      store, io, online, rank, isStaff, RANKS, publicUser, quizBot, now, roomsSummary
    });

    res.json({ ok: true, output: resultMsg.trim() || 'تم تنفيذ الأمر' });
  });

  return router;
}

/**
 * Executes slash admin commands typed in chat (/mute, /unmute, /kick, /ban, /promote, etc.)
 */
function handleAdminCommand(socket, user, room, text, ctx) {
  const { store, io, online, rank, isStaff, RANKS, publicUser, quizBot, now, roomsSummary } = ctx;

  const trimmed = text.trim();
  if (!trimmed.startsWith('/') && !trimmed.startsWith('!')) return false;

  const parts = trimmed.slice(1).split(/\s+/);
  const cmd = parts[0].toLowerCase();
  const arg1 = parts[1] || '';

  const reply = (msg) => {
    socket.emit('system_message', { roomId: room.id, text: msg, at: now() });
    socket.emit('error_alert', { message: msg });
  };

  if (!isStaff(user)) {
    reply('⚠️ أوامر الإدارة متاحة للمشرفين والمدير فقط');
    return true;
  }

  const findUser = (query) => {
    if (!query) return null;
    const qLower = query.toLowerCase().replace(/^@/, '');
    const uid = store.state.names[qLower];
    if (uid && store.state.users[uid]) return store.state.users[uid];
    return Object.values(store.state.users).find(u => u.name.toLowerCase() === qLower || u.id === query);
  };

  switch (cmd) {
    case 'help':
    case 'اوامر':
    case 'أوامر':
      reply('📜 أوامر الإدارة المتاحة:\n' +
        '/mute <اسم> - كتم عضو\n' +
        '/unmute <اسم> - فك كتم عضو\n' +
        '/ghost <اسم> - وضع الشبح (كتم صامت)\n' +
        '/unghost <اسم> - إلغاء الشبح\n' +
        '/kick <اسم> - طرد عضو من الغرفة\n' +
        '/ban <اسم> - حظر عتاد الجهاز نهائياً\n' +
        '/promote <اسم> <MODERATOR|VIP_DIAMOND|REGULAR> - تغيير الرتبة\n' +
        '/quiz - طرح سؤال مسابقة الآن (ست وداد)\n' +
        '/broadcast <نص> - إرسال إعلان عام لكافة الغرف\n' +
        '/lock public|private on|off - قفل أو فتح الغرفة\n' +
        '/topic <نص> - تحديث إعلان الروم');
      return true;

    case 'mute':
    case 'كتم': {
      const target = findUser(arg1);
      if (!target) { reply('⚠️ لم يتم العثور على العضو: ' + arg1); return true; }
      if (rank(target) >= rank(user) && target.id !== user.id) { reply('⚠️ لا يمكنك كتم عضو رتبته مساوية أو أعلى منك'); return true; }
      target.isMuted = true; store.flush();
      const tp = online.get(target.id);
      if (tp) io.to(tp.roomId).emit('user_updated', { user: publicUser(target) });
      io.to(room.id).emit('system_message', { roomId: room.id, text: `🔇 قام المشرف ${user.name} بكتم ${target.name}`, at: now() });
      reply(`✅ تم كتم ${target.name} بنجاح`);
      return true;
    }

    case 'unmute':
    case 'فك_كتم': {
      const target = findUser(arg1);
      if (!target) { reply('⚠️ لم يتم العثور على العضو: ' + arg1); return true; }
      target.isMuted = false; store.flush();
      const tp = online.get(target.id);
      if (tp) io.to(tp.roomId).emit('user_updated', { user: publicUser(target) });
      io.to(room.id).emit('system_message', { roomId: room.id, text: `🔊 قام المشرف ${user.name} بفك كتم ${target.name}`, at: now() });
      reply(`✅ تم فك كتم ${target.name} بنجاح`);
      return true;
    }

    case 'ghost':
    case 'شبح': {
      const target = findUser(arg1);
      if (!target) { reply('⚠️ لم يتم العثور على العضو: ' + arg1); return true; }
      if (rank(target) >= rank(user) && target.id !== user.id) { reply('⚠️ لا يمكنك تطبيق الشبح على عضو برتبتك'); return true; }
      target.isGhost = true; store.flush();
      reply(`👻 تم تفعيل وضع الشبح على ${target.name}`);
      return true;
    }

    case 'unghost':
    case 'الغاء_شبح': {
      const target = findUser(arg1);
      if (!target) { reply('⚠️ لم يتم العثور على العضو: ' + arg1); return true; }
      target.isGhost = false; store.flush();
      reply(`👁️ تم إلغاء وضع الشبح عن ${target.name}`);
      return true;
    }

    case 'kick':
    case 'طرد': {
      const target = findUser(arg1);
      if (!target) { reply('⚠️ لم يتم العثور على العضو: ' + arg1); return true; }
      if (rank(target) >= rank(user) && target.id !== user.id) { reply('⚠️ لا يمكنك طرد عضو برتبتك'); return true; }
      const tp = online.get(target.id);
      if (tp) {
        io.to(tp.socketId).emit('force_disconnect', { reason: 'تم طردك بواسطة المشرف ' + user.name });
        io.sockets.sockets.get(tp.socketId)?.disconnect(true);
      }
      io.to(room.id).emit('system_message', { roomId: room.id, text: `🚪 تم طرد ${target.name} من الغرفة`, at: now() });
      reply(`✅ تم طرد ${target.name}`);
      return true;
    }

    case 'ban':
    case 'حظر': {
      const target = findUser(arg1);
      if (!target) { reply('⚠️ لم يتم العثور على العضو: ' + arg1); return true; }
      if (rank(target) >= rank(user) && target.id !== user.id) { reply('⚠️ لا يمكنك حظر عضو برتبتك'); return true; }
      for (const d of (target.devices || [])) store.state.bannedDevices[d] = { by: user.name, at: now() };
      store.flush();
      const tp = online.get(target.id);
      if (tp) {
        io.to(tp.socketId).emit('force_disconnect', { reason: 'تم حظر جهازك نهائياً' });
        io.sockets.sockets.get(tp.socketId)?.disconnect(true);
      }
      io.to(room.id).emit('system_message', { roomId: room.id, text: `⛔ تم حظر عتاد جهاز ${target.name} نهائياً`, at: now() });
      reply(`✅ تم حظر جهاز ${target.name} بنجاح`);
      return true;
    }

    case 'promote':
    case 'ترقية': {
      const target = findUser(arg1);
      const newRank = (parts[2] || '').toUpperCase();
      if (!target) { reply('⚠️ لم يتم العثور على العضو: ' + arg1); return true; }
      if (!['MODERATOR', 'VIP_DIAMOND', 'REGULAR'].includes(newRank)) {
        reply('⚠️ الرتب المتاحة: MODERATOR, VIP_DIAMOND, REGULAR');
        return true;
      }
      if (user.rank !== 'OWNER' && newRank === 'MODERATOR') {
        reply('⚠️ ترقية المشرفين للمالك فقط');
        return true;
      }
      target.rank = newRank; store.flush();
      const tp = online.get(target.id);
      if (tp) io.to(tp.roomId).emit('user_updated', { user: publicUser(target) });
      io.to(room.id).emit('system_message', { roomId: room.id, text: `🎖️ قام المشرف بترقية ${target.name} إلى ${newRank}`, at: now() });
      reply(`✅ تم تغيير رتبة ${target.name} إلى ${newRank}`);
      return true;
    }

    case 'quiz':
    case 'مسابقة': {
      quizBot.askQuestion(room.id);
      reply('🎮 تم طرح سؤال مسابقة الآن في الغرفة بواسطة ست وداد');
      return true;
    }

    case 'broadcast':
    case 'اعلان':
    case 'تعميم': {
      const bText = parts.slice(1).join(' ').trim();
      if (!bText) { reply('⚠️ اكتب نص الإعلان: /broadcast نص'); return true; }
      io.emit('broadcast', { text: bText, by: user.name, at: now() });
      reply('📢 تم إرسال الإعلان لجميع الغرف');
      return true;
    }

    case 'lock':
    case 'قفل': {
      const what = (parts[1] || '').toLowerCase();
      const val = (parts[2] || '').toLowerCase();
      const isLock = val === 'on' || val === 'true' || val === '1' || val === 'قفل';
      if (what === 'public' || what === 'عام') {
        room.lockPublic = isLock;
        io.to(room.id).emit('room_updated', { id: room.id, lockPublic: !!room.lockPublic, lockPrivate: !!room.lockPrivate });
        io.to(room.id).emit('system_message', { roomId: room.id, text: `🔒 ${isLock ? 'تم قفل الشات العام' : 'تم فتح الشات العام'} بواسطة المشرف`, at: now() });
        reply(`✅ تم ${isLock ? 'قفل' : 'فتح'} الشات العام`);
      } else if (what === 'private' || what === 'خاص') {
        room.lockPrivate = isLock;
        io.to(room.id).emit('room_updated', { id: room.id, lockPublic: !!room.lockPublic, lockPrivate: !!room.lockPrivate });
        reply(`✅ تم ${isLock ? 'قفل' : 'فتح'} المحادثات الخاصة`);
      } else {
        reply('⚠️ الاستخدام: /lock public on|off أو /lock private on|off');
      }
      return true;
    }

    case 'topic':
    case 'اعلان_الروم': {
      const newTopic = parts.slice(1).join(' ').trim();
      room.topic = newTopic;
      store.save();
      io.to(room.id).emit('room_updated', { id: room.id, topic: newTopic });
      io.emit('rooms', { rooms: roomsSummary() });
      reply('✅ تم تحديث إعلان الروم: ' + newTopic);
      return true;
    }

    default:
      reply('⚠️ أمر غير معروف. اكتب /help لعرض قائمة الأوامر');
      return true;
  }
}

module.exports = { createAdminRouter, handleAdminCommand };
