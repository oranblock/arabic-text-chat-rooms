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
    if (typeof youtubeId === 'string' && youtubeId.trim()) {
      const filter = require('./filter');
      const cleanYt = filter.youtubeId(youtubeId) || youtubeId.trim();
      room.youtubeId = cleanYt;
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
    const { roomId, botId } = req.body || {};
    const targetRoom = roomId || 'games';
    const q = quizBot.askQuestion(targetRoom, botId);
    res.json({ ok: true, question: q });
  });

  // Bot Management Endpoints
  router.get('/bots', requireStaff, (_req, res) => {
    res.json({
      ok: true,
      bots: Array.from(quizBot.bots.values()),
      totalQuestions: quizBot.questions.length,
      activeQuestions: Object.fromEntries(
        Array.from(quizBot.activeQuestions.entries()).map(([rId, q]) => [rId, { q: q.q, botName: q.botName, askedAt: q.askedAt }])
      ),
      autoRooms: Array.from(quizBot.autoRooms.keys())
    });
  });

  router.post('/bots/import', requireStaff, (req, res) => {
    const importRes = quizBot.importData(req.body);
    if (!importRes.ok) return res.status(400).json(importRes);
    res.json(importRes);
  });

  router.post('/bots/summon', requireStaff, (req, res) => {
    const { roomId, botId } = req.body || {};
    const targetRoom = roomId || 'games';
    const summoned = quizBot.summonBot(targetRoom, botId, req.adminUser);
    res.json({ ok: !!summoned, bot: summoned });
  });

  router.post('/bots/dismiss', requireStaff, (req, res) => {
    const { roomId } = req.body || {};
    const targetRoom = roomId || 'games';
    const dismissed = quizBot.dismissBot(targetRoom, req.adminUser);
    res.json({ ok: true, dismissed });
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

  // Bot commands
  if (cmd === 'bots' || cmd === 'البوتات' || cmd === 'قائمة_البوتات') {
    const list = Array.from(quizBot.bots.values()).map((b, i) => `${i + 1}. ${b.name} (${b.id}) - ${b.role}`).join('\n');
    reply(`🤖 قائمة البوتات المتاحة:\n${list}\n\n💡 لاستدعاء بوت: /bot summon <معرف_البوت>`);
    return true;
  }

  if (cmd === 'bot' || cmd === 'بوت') {
    const sub = (parts[1] || '').toLowerCase();
    if (!sub || sub === 'help' || sub === 'مساعدة') {
      reply(`🤖 أوامر إدارة البوتات:
/bot summon <معرف_البوت> (استدعاء بوت للغرفة)
/bot dismiss (صرف البوت وإيقاف مسابقاته)
/bot quiz (طرح سؤال مسابقة فوري)
/bot auto on [ثواني] (تشغيل المسابقات التلقائية)
/bot auto off (إيقاف المسابقات التلقائية)
/bot import <نص_json> (استيراد أسئلة أو بوتات)
/bots (عرض قائمة جميع البوتات)`);
      return true;
    }

    if (sub === 'summon' || sub === 'استدعاء') {
      if (!isStaff(user)) { reply('⚠️ استدعاء البوتات مخصص للإدارة والمشرفين'); return true; }
      const botQuery = parts.slice(2).join(' ').trim() || 'bot_widad';
      const summoned = quizBot.summonBot(room.id, botQuery, user);
      if (!summoned) { reply('⚠️ لم يتم العثور على البوت المطلوب'); return true; }
      reply(`✅ تم استدعاء [ ${summoned.name} ] إلى الغرفة بنجاح`);
      return true;
    }

    if (sub === 'dismiss' || sub === 'صرف' || sub === 'طرد' || sub === 'stop' || sub === 'ايقاف') {
      if (!isStaff(user)) { reply('⚠️ صرف البوتات مخصص للإدارة والمشرفين'); return true; }
      const dismissed = quizBot.dismissBot(room.id, user);
      reply(`🛑 تم صرف البوت [ ${dismissed ? dismissed.name : 'البوت'} ] وتوقفت مسابقاته في الغرفة`);
      return true;
    }

    if (sub === 'quiz' || sub === 'سؤال' || sub === 'مسابقة') {
      const qRes = quizBot.askQuestion(room.id);
      if (qRes && qRes.alreadyActive) {
        reply(`⚠️ يوجد سؤال قيد الحل حالياً في الغرفة: ${qRes.q} (متبقي ${qRes.remainingSec} ثانية)`);
      } else if (qRes && qRes.ok) {
        reply(`🎮 تم طرح سؤال مسابقة الآن بواسطة ${qRes.bot.name}`);
      }
      return true;
    }

    if (sub === 'auto' || sub === 'تلقائي') {
      if (!isStaff(user)) { reply('⚠️ التحكم بالوضع التلقائي مخصص للإدارة'); return true; }
      const mode = (parts[2] || '').toLowerCase();
      if (mode === 'on' || mode === 'تشغيل' || mode === '1') {
        const sec = parseInt(parts[3], 10) || 60;
        quizBot.enableAuto(room.id, sec);
        reply(`✅ تم تفعيل الوضع التلقائي للمسابقات كل ${sec} ثانية`);
      } else {
        quizBot.disableAuto(room.id);
        reply('🛑 تم إيقاف الوضع التلقائي للمسابقات. سيبقى البوت صامتاً حتى يتم استدعاؤه');
      }
      return true;
    }

    if (sub === 'import' || sub === 'استيراد') {
      if (user.rank !== 'OWNER') { reply('⚠️ استيراد الأسئلة والبوتات محصور للمالك العام OWNER'); return true; }
      const jsonStr = parts.slice(2).join(' ').trim();
      try {
        const parsed = JSON.parse(jsonStr);
        const res = quizBot.importData(parsed);
        if (res.ok) {
          reply(`🎉 تم استيراد ${res.importedQuestions} سؤال و ${res.importedBots} بوت بنجاح! الإجمالي: ${res.totalQuestions} سؤال.`);
        } else {
          reply(`⚠️ خطأ في الاستيراد: ${res.error}`);
        }
      } catch (e) {
        reply(`⚠️ صيغة JSON غير صحيحة: ${e.message}\nمثال: /bot import {"questions":[{"q":"سؤال","a":"جواب","points":10}]}`);
      }
      return true;
    }
  }

  // Quick summon aliases
  if (cmd === 'summon' || cmd === 'استدعاء') {
    if (!isStaff(user)) { reply('⚠️ استدعاء البوتات مخصص للإدارة والمشرفين'); return true; }
    const botQuery = parts.slice(1).join(' ').trim() || 'bot_widad';
    const summoned = quizBot.summonBot(room.id, botQuery, user);
    if (!summoned) { reply('⚠️ لم يتم العثور على البوت المطلوب'); return true; }
    reply(`✅ تم استدعاء [ ${summoned.name} ] إلى الغرفة بنجاح`);
    return true;
  }

  if (cmd === 'dismiss' || cmd === 'صرف' || cmd === 'صرف_البوت' || cmd === 'طرد_البوت') {
    if (!isStaff(user)) { reply('⚠️ صرف البوتات مخصص للإدارة والمشرفين'); return true; }
    const dismissed = quizBot.dismissBot(room.id, user);
    reply(`🛑 تم صرف البوت [ ${dismissed ? dismissed.name : 'البوت'} ] من الغرفة`);
    return true;
  }

  // Commands available to all users in the room
  if (cmd === 'yt' || cmd === 'youtube' || cmd === 'يوتيوب' || cmd === 'play') {
    const target = parts.slice(1).join(' ').trim();
    if (!target) {
      reply('⚠️ اكتب رابط اليوتيوب أو معرّف الفيديو: /yt رابط_الفيديو');
      return true;
    }
    const isStaffOrVip = user.rank === 'OWNER' || user.rank === 'MODERATOR' || user.rank === 'VIP_DIAMOND';
    // Anti-spam: Prevent regular users from cutting off an active video that started < 2 mins ago
    const isCurrentlyPlaying = room.youtubeId && room.youtubeStartedAt && (now() - room.youtubeStartedAt < 120000);
    if (!isStaffOrVip && isCurrentlyPlaying) {
      reply(`⚠️ يوجد فيديو معروض حالياً (${room.youtubeTitle || 'فيديو الروم'}). لحفظ النظام يرجى إضافته لقائمة الانتظار عبر: /q رابط_الفيديو 📋`);
      return true;
    }
    const filter = require('./filter');
    const ytId = filter.youtubeId(target) || target;
    room.youtubeId = ytId;
    room.youtubeTitle = 'فيديو بواسطة ' + user.name;
    room.youtubeStartedAt = now();
    room.youtubeStartedBy = user.name;
    room.isWelcome = false;
    store.save();
    io.to(room.id).emit('youtube_updated', {
      videoId: ytId,
      videoTitle: room.youtubeTitle,
      startedAt: room.youtubeStartedAt,
      startedBy: user.name,
      offset: 0,
      status: 'play',
      by: user.name,
      isWelcome: false
    });
    io.to(room.id).emit('system_message', {
      roomId: room.id,
      text: `🎬 قام ${user.name} بتشغيل فيديو يوتيوب متزامن بالروم 🎵`,
      at: now()
    });
    return true;
  }

  // Stop video command
  if (cmd === 'stop_yt' || cmd === 'stop' || cmd === 'ايقاف' || cmd === 'وقف') {
    const isStaffOrVip = user.rank === 'OWNER' || user.rank === 'MODERATOR' || user.rank === 'VIP_DIAMOND';
    if (!isStaffOrVip) {
      reply('⚠️ إيقاف الفيديو مخصص للمشرفين والأعضاء المميزين VIP 💎');
      return true;
    }
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
      status: 'stop',
      by: user.name,
      isWelcome: false
    });
    io.to(room.id).emit('system_message', {
      roomId: room.id,
      text: `🛑 قام ${user.name} بإيقاف الفيديو في الغرفة`,
      at: now()
    });
    return true;
  }

  // Welcome Video commands for Staff
  if (cmd === 'welcome_yt' || cmd === 'ترحيب' || cmd === 'فيديو_ترحيب') {
    if (!isStaff(user)) {
      reply('⚠️ تعيين فيديو الترحيب مخصص للإدارة والمشرفين فقط');
      return true;
    }
    const target = parts.slice(1).join(' ').trim();
    const filter = require('./filter');
    const ytId = filter.youtubeId(target) || target;
    if (!ytId) {
      reply('⚠️ اكتب رابط اليوتيوب: /ترحيب رابط_اليوتيوب');
      return true;
    }
    room.welcomeVideoId = ytId;
    room.welcomeVideoTitle = `فيديو ترحيبي - ${room.title}`;
    room.youtubeId = ytId;
    room.youtubeTitle = room.welcomeVideoTitle;
    room.youtubeStartedAt = now();
    room.youtubeStartedBy = 'فيديو ترحيبي';
    room.isWelcome = true;
    store.save();
    io.to(room.id).emit('youtube_updated', {
      videoId: ytId,
      videoTitle: room.youtubeTitle,
      startedAt: room.youtubeStartedAt,
      startedBy: 'فيديو ترحيبي',
      offset: 0,
      status: 'play',
      by: 'الإدارة',
      isWelcome: true
    });
    io.to(room.id).emit('system_message', {
      roomId: room.id,
      text: `🎬 قام المشرف ${user.name} بتعيين فيديو ترحيبي رسمي للغرفة 🌟`,
      at: now()
    });
    reply(`✅ تم تعيين الفيديو الترحيبي بنجاح للغرفة: ${room.title}`);
    return true;
  }

  if (cmd === 'del_welcome' || cmd === 'حذف_الترحيب') {
    if (!isStaff(user)) {
      reply('⚠️ حذف فيديو الترحيب مخصص للإدارة والمشرفين فقط');
      return true;
    }
    room.welcomeVideoId = '';
    room.welcomeVideoTitle = '';
    if (room.isWelcome) {
      room.youtubeId = '';
      room.youtubeTitle = '';
      room.youtubeStartedAt = 0;
      room.youtubeStartedBy = '';
      room.isWelcome = false;
      io.to(room.id).emit('youtube_updated', {
        videoId: '',
        videoTitle: '',
        startedAt: 0,
        startedBy: '',
        offset: 0,
        status: 'stop',
        by: user.name,
        isWelcome: false
      });
    }
    store.save();
    reply('✅ تم حذف الفيديو الترحيبي للغرفة');
    return true;
  }

  // Queue commands for VIP & Room members
  if (cmd === 'queue' || cmd === 'q' || cmd === 'انتظار') {
    const target = parts.slice(1).join(' ').trim();
    if (!target) {
      const q = room.youtubeQueue || [];
      if (q.length === 0) {
        reply('قائمة الانتظار فارغة حالياً. أضف فيديو عبر: /queue رابط_الفيديو');
      } else {
        const listStr = q.map((item, idx) => `${idx + 1}. ${item.title} (بواسطة ${item.by})`).join('\n');
        reply(`📋 قائمة تشغيل الفيديوهات (${q.length}):\n${listStr}`);
      }
      return true;
    }
    const filter = require('./filter');
    const ytId = filter.youtubeId(target) || target;
    if (!room.youtubeQueue) room.youtubeQueue = [];
    room.youtubeQueue.push({ videoId: ytId, title: 'فيديو من ' + user.name, by: user.name, at: now() });
    store.save();
    io.to(room.id).emit('system_message', {
      roomId: room.id,
      text: `📋 أضاف ${user.name} فيديو جديد لقائمة الانتظار (رقم ${room.youtubeQueue.length}) 🎵`,
      at: now()
    });
    return true;
  }

  if (cmd === 'skip' || cmd === 'تخطي') {
    const isVipOrStaff = user.rank === 'VIP_DIAMOND' || user.rank === 'MODERATOR' || user.rank === 'OWNER';
    if (!isVipOrStaff) {
      reply('⚠️ ميزة تخطي الفيديو مخصصة للأعضاء المميزين VIP والإدارة فقط 💎');
      return true;
    }
    const q = room.youtubeQueue || [];
    if (q.length === 0) {
      reply('⚠️ لا يوجد فيديو تالي في قائمة الانتظار');
      return true;
    }
    const nextItem = q.shift();
    room.youtubeId = nextItem.videoId;
    room.youtubeTitle = nextItem.title;
    room.youtubeStartedAt = now();
    room.youtubeStartedBy = nextItem.by;
    store.save();
    io.to(room.id).emit('youtube_updated', {
      videoId: nextItem.videoId,
      videoTitle: nextItem.title,
      startedAt: room.youtubeStartedAt,
      startedBy: nextItem.by,
      offset: 0,
      status: 'play',
      by: user.name
    });
    io.to(room.id).emit('system_message', {
      roomId: room.id,
      text: `⏭️ قام ${user.name} بتخطي الفيديو وتشغيل التالي: ${nextItem.title} (بواسطة ${nextItem.by})`,
      at: now()
    });
    return true;
  }

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
        '/quiz - طرح سؤال مسابقة الآن (مسابقات الكلمات المبعثرة)\n' +
        '/yt <رابط أو ID> - تغيير وتزامن فيديو اليوتيوب بالروم\n' +
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
      const qRes = quizBot.askQuestion(room.id);
      if (qRes && qRes.alreadyActive) {
        reply(`⚠️ يوجد سؤال قيد الحل حالياً في الغرفة: ${qRes.q} (متبقي ${qRes.remainingSec} ثانية)`);
      } else if (qRes && qRes.ok) {
        reply(`🎮 تم طرح سؤال مسابقة الآن بواسطة ${qRes.bot.name}`);
      }
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

    case 'yt':
    case 'youtube':
    case 'يوتيوب': {
      const target = parts.slice(1).join(' ').trim();
      if (!target) { reply('⚠️ اكتب رابط اليوتيوب أو معرّف الفيديو: /yt رابط_الفيديو'); return true; }
      const filter = require('./filter');
      const ytId = filter.youtubeId(target) || target;
      room.youtubeId = ytId;
      room.youtubeTitle = 'فيديو بواسطة ' + user.name;
      room.youtubeStartedAt = now();
      room.youtubeStartedBy = user.name;
      room.isWelcome = false;
      store.save();
      io.to(room.id).emit('youtube_updated', {
        videoId: ytId,
        videoTitle: room.youtubeTitle,
        startedAt: room.youtubeStartedAt,
        startedBy: user.name,
        offset: 0,
        status: 'play',
        by: user.name,
        isWelcome: false
      });
      reply('🎬 تم تغيير وتزامن فيديو اليوتيوب في الغرفة: ' + ytId);
      return true;
    }

    default:
      reply('⚠️ أمر غير معروف. اكتب /help لعرض قائمة الأوامر');
      return true;
  }
}

module.exports = { createAdminRouter, handleAdminCommand };
