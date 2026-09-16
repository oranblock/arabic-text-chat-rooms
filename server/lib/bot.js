'use strict';
/**
 * Advanced Multi-Bot Entertainment & Quiz Management Engine
 * Features:
 * 1. Multiple Bots: ست وداد, حزورة الكلمات, أبو حيدر المضياف, قبسات إيمانية.
 * 2. Strict Anti-Spam: Bots NEVER speak first or auto-spam. Staff must summon them.
 * 3. Lifecycle Resolution: Immediate reply upon correct answer OR announcement when time expires.
 * 4. Bot Importer: Load and hot-import bots and questions from server/data/bots.json or API.
 */

const fs = require('fs');
const path = require('path');

const BOTS_FILE = path.join(__dirname, '../data/bots.json');

class BotManager {
  constructor(io, store, makeMessage) {
    this.io = io;
    this.store = store;
    this.makeMessage = makeMessage;

    this.bots = new Map();           // botId -> botConfig
    this.questions = [];             // list of { category, q, a, points }
    this.activeQuestions = new Map();// roomId -> { q, a, points, botId, timer, askedAt }
    this.roomBots = new Map();       // roomId -> botId (active summoned bot)
    this.autoRooms = new Map();      // roomId -> intervalMs (if explicitly enabled by staff)
    this.autoTimers = new Map();     // roomId -> timeout
    this.scores = new Map();         // userId -> points

    this.loadData();
  }

  // Load bots and questions from bots.json
  loadData() {
    try {
      if (fs.existsSync(BOTS_FILE)) {
        const raw = fs.readFileSync(BOTS_FILE, 'utf8');
        const parsed = JSON.parse(raw);
        if (Array.isArray(parsed.bots)) {
          this.bots.clear();
          for (const b of parsed.bots) this.bots.set(b.id, b);
        }
        if (Array.isArray(parsed.questions) && parsed.questions.length > 0) {
          this.questions = parsed.questions;
        }
      }
    } catch (e) {
      console.error('[BotManager] Error loading bots.json:', e.message);
    }

    // Default fallback bots if none loaded
    if (this.bots.size === 0) {
      this.bots.set('bot_widad', {
        id: 'bot_widad',
        name: 'ست وداد 🤖',
        avatarUrl: 'https://api.dicebear.com/7.x/bottts/png?seed=Widad',
        rank: 'BOT',
        customHexColor: '#00E5FF',
        role: 'trivia',
        greeting: 'هلا والله بالحبايب! أني ست وداد ومعاكم للمسابقات الثقافية والمعلومات العامة 🇮🇶✨'
      });
      this.bots.set('bot_scramble', {
        id: 'bot_scramble',
        name: 'حزورة الكلمات 🎮',
        avatarUrl: 'https://api.dicebear.com/7.x/bottts/png?seed=ScrambleWords',
        rank: 'BOT',
        customHexColor: '#FF9800',
        role: 'scramble',
        greeting: 'يا هلا بأصحاب الذكاء! جاهزين لحل الكلمات المبعثرة والألغاز؟ 🧩🔥'
      });
    }

    if (this.questions.length === 0) {
      this.questions = [
        { category: 'culture', q: 'ما هي عاصمة العراق في عهد الدولة العباسية؟', a: 'بغداد', points: 10 },
        { category: 'scramble', q: 'رتب حروف الكلمة العراقية: (د - غ - ب - ا - د)', a: 'بغداد', points: 10 },
        { category: 'geography', q: 'نهر يمر في بغداد يقسمها إلى كرخ ورصافة؟', a: 'دجلة', points: 10 },
        { category: 'history', q: 'في أي مدينة تقع المئذنة الملوية الشهيرة؟', a: 'سامراء', points: 10 },
        { category: 'proverbs', q: 'أكمل المثل: فرخ البط ...؟', a: 'عوام', points: 10 }
      ];
    }
  }

  // Import bots or questions dynamically
  importData(data) {
    if (!data || typeof data !== 'object') {
      return { ok: false, error: 'بيانات JSON غير صالحة' };
    }

    let importedBots = 0;
    let importedQuestions = 0;

    if (Array.isArray(data.bots)) {
      for (const b of data.bots) {
        if (b.id && b.name) {
          this.bots.set(b.id, {
            id: b.id,
            name: b.name,
            avatarUrl: b.avatarUrl || 'https://api.dicebear.com/7.x/bottts/png?seed=' + b.id,
            rank: 'BOT',
            customHexColor: b.customHexColor || b.color || '#00E5FF',
            role: b.role || 'trivia',
            greeting: b.greeting || `مرحباً! أنا ${b.name} جاهز لمساعدتكم 🤖`
          });
          importedBots++;
        }
      }
    }

    if (Array.isArray(data.questions)) {
      const valid = data.questions.filter(q => q && q.q && q.a).map(q => ({
        category: q.category || 'general',
        q: String(q.q).trim(),
        a: String(q.a).trim(),
        points: parseInt(q.points, 10) || 10
      }));
      if (valid.length > 0) {
        if (data.replace === true) {
          this.questions = valid;
        } else {
          this.questions.push(...valid);
        }
        importedQuestions = valid.length;
      }
    }

    // Persist updated definitions to bots.json
    try {
      const exportObj = {
        bots: Array.from(this.bots.values()),
        questions: this.questions
      };
      fs.writeFileSync(BOTS_FILE, JSON.stringify(exportObj, null, 2));
    } catch (e) {
      console.error('[BotManager] Failed to persist bots.json:', e.message);
    }

    return {
      ok: true,
      importedBots,
      importedQuestions,
      totalBots: this.bots.size,
      totalQuestions: this.questions.length
    };
  }

  // Default bot user for backward compatibility
  get user() {
    return this.bots.get('bot_widad') || this.bots.values().next().value;
  }

  // Get bot by ID
  getBot(botId) {
    if (!botId) return this.user;
    return this.bots.get(botId) || Array.from(this.bots.values()).find(b => b.name.includes(botId)) || this.user;
  }

  // Get bots active for a room (to show in user list)
  getBotsForRoom(roomId) {
    const botId = this.roomBots.get(roomId);
    if (botId && this.bots.has(botId)) {
      return [this.bots.get(botId)];
    }
    // In games room, show default bot if not dismissed
    if (roomId === 'games' && botId !== 'none') {
      return [this.bots.get('bot_scramble') || this.user];
    }
    return [];
  }

  // Summon a specific bot to a room
  summonBot(roomId, botIdQuery = 'bot_widad', byUser = null) {
    const bot = this.getBot(botIdQuery);
    if (!bot) return null;

    this.roomBots.set(roomId, bot.id);
    const greeting = bot.greeting || `مرحباً جميعاً! أنا ${bot.name} تم استدعائي للغرفة 🌟`;
    const msg = this.makeMessage(bot, roomId, greeting);
    this.store.pushMessage(roomId, msg);
    this.io.to(roomId).emit('new_message', msg);

    // Notify room of user list change with the summoned bot
    const users = this.getBotsForRoom(roomId);
    this.io.to(roomId).emit('system_message', {
      roomId,
      text: `🤖 قام ${byUser ? byUser.name : 'المشرف'} باستدعاء البوت [ ${bot.name} ] للغرفة`,
      at: Date.now()
    });

    return bot;
  }

  // Dismiss a bot from a room
  dismissBot(roomId, byUser = null) {
    const currentBotId = this.roomBots.get(roomId) || (roomId === 'games' ? 'bot_scramble' : null);
    const bot = currentBotId ? this.getBot(currentBotId) : this.user;

    // Clear active question & timers in this room
    this.clearQuestion(roomId);
    this.disableAuto(roomId);
    this.roomBots.set(roomId, 'none');

    const farewell = `👋 في أمان الله وحفظه، كان معكم ${bot.name}. استمتعوا بالدردشة! 🌹`;
    const msg = this.makeMessage(bot, roomId, farewell);
    this.store.pushMessage(roomId, msg);
    this.io.to(roomId).emit('new_message', msg);

    this.io.to(roomId).emit('system_message', {
      roomId,
      text: `🛑 قام ${byUser ? byUser.name : 'المشرف'} بصرف البوت وإيقاف المسابقات في الغرفة`,
      at: Date.now()
    });

    return bot;
  }

  // Clear active question in a room
  clearQuestion(roomId) {
    const existing = this.activeQuestions.get(roomId);
    if (existing && existing.timer) {
      clearTimeout(existing.timer);
    }
    this.activeQuestions.delete(roomId);
  }

  // Ask a single question (invoked by /quiz or auto mode)
  askQuestion(roomId = 'games', botId = null, timeoutSec = 60) {
    // If a question is already running in this room, do not spam a duplicate!
    if (this.activeQuestions.has(roomId)) {
      const active = this.activeQuestions.get(roomId);
      const remainingSec = Math.max(1, Math.floor((60000 - (Date.now() - active.askedAt)) / 1000));
      return { ok: false, alreadyActive: true, q: active.q, remainingSec };
    }

    const targetBotId = botId || this.roomBots.get(roomId) || (roomId === 'games' ? 'bot_scramble' : 'bot_widad');
    const bot = this.getBot(targetBotId);

    // Pick a question (matching category if possible)
    let candidates = this.questions;
    if (bot.role === 'scramble') {
      candidates = this.questions.filter(q => q.category === 'scramble');
    } else if (bot.role === 'proverbs') {
      candidates = this.questions.filter(q => q.category === 'proverbs');
    } else if (bot.role === 'religious') {
      candidates = this.questions.filter(q => q.category === 'religious');
    }
    if (!candidates || candidates.length === 0) candidates = this.questions;

    const item = candidates[Math.floor(Math.random() * candidates.length)];
    const pts = item.points || 10;
    const text = `🎮 ${bot.name}: ${item.q}\n(اكتب الجواب بالشات واربح ${pts} نقاط! 🏆 | لديك ${timeoutSec} ثانية ⏳)`;

    const msg = this.makeMessage(bot, roomId, text);
    this.store.pushMessage(roomId, msg);
    this.io.to(roomId).emit('new_message', msg);

    // Set expiration timer (Resolution when time expires!)
    const timer = setTimeout(() => {
      this.handleQuestionExpired(roomId, item, bot);
    }, timeoutSec * 1000);

    const questionEntry = {
      q: item.q,
      a: item.a,
      points: pts,
      botId: bot.id,
      botName: bot.name,
      askedAt: Date.now(),
      timer
    };

    this.activeQuestions.set(roomId, questionEntry);
    return { ok: true, item, bot };
  }

  // Called when 60s passes without any correct answer
  handleQuestionExpired(roomId, item, bot) {
    const current = this.activeQuestions.get(roomId);
    if (!current) return;

    this.activeQuestions.delete(roomId);

    const timeoutText = `⏰ انتهى الوقت المحدد للسؤال ولم يتمكن أحد من الإجابة!\n💡 الجواب الصحيح كان: [ ${item.a} ]`;
    const msg = this.makeMessage(bot, roomId, timeoutText);
    this.store.pushMessage(roomId, msg);
    this.io.to(roomId).emit('new_message', msg);

    // Only if staff specifically turned on auto mode, wait 30s before asking next
    if (this.autoRooms.has(roomId)) {
      const delay = this.autoRooms.get(roomId) || 45000;
      const nextTimer = setTimeout(() => {
        if (this.autoRooms.has(roomId)) {
          this.askQuestion(roomId, bot.id);
        }
      }, delay);
      this.autoTimers.set(roomId, nextTimer);
    }
    // Otherwise: STAY COMPLETELY SILENT! Zero spam!
  }

  // Check incoming chat message against active question
  checkAnswer(roomId, user, text) {
    const active = this.activeQuestions.get(roomId);
    if (!active || !text) return false;

    const norm = (s) => s.trim().toLowerCase()
      .replace(/[\u064B-\u065Fأإآ]/g, (m) => m === 'ة' ? 'ه' : 'ا')
      .replace(/[^a-zA-Z0-9\u0621-\u064A]/g, '');

    const cleanUser = norm(text);
    const cleanAns = norm(active.a);

    if (cleanUser.length > 0 && cleanAns.length > 0 && (cleanUser.includes(cleanAns) || cleanUser === cleanAns)) {
      // Clear question and timer immediately
      if (active.timer) clearTimeout(active.timer);
      this.activeQuestions.delete(roomId);

      const earnedPts = active.points || 10;
      const totalPts = (this.scores.get(user.id) || 0) + earnedPts;
      this.scores.set(user.id, totalPts);

      const bot = this.getBot(active.botId);
      const reply = `🎉 كفووو يا @${user.name}! إجابتك صحيحة [ ${active.a} ] وربحت ${earnedPts} نقاط! (مجموع نقاطك: ${totalPts} 🏆)`;
      const msg = this.makeMessage(bot, roomId, reply);
      this.store.pushMessage(roomId, msg);
      this.io.to(roomId).emit('new_message', msg);

      // Only schedule next question if staff explicitly enabled auto mode
      if (this.autoRooms.has(roomId)) {
        const delay = this.autoRooms.get(roomId) || 30000;
        const nextTimer = setTimeout(() => {
          if (this.autoRooms.has(roomId)) {
            this.askQuestion(roomId, bot.id);
          }
        }, delay);
        this.autoTimers.set(roomId, nextTimer);
      }
      // Otherwise: STAY COMPLETELY SILENT! Zero spam!
      return true;
    }
    return false;
  }

  // Enable explicit auto-mode for a room (staff command only)
  enableAuto(roomId, intervalSec = 45) {
    this.autoRooms.set(roomId, intervalSec * 1000);
    this.askQuestion(roomId);
  }

  // Disable auto-mode for a room
  disableAuto(roomId) {
    this.autoRooms.delete(roomId);
    const t = this.autoTimers.get(roomId);
    if (t) { clearTimeout(t); this.autoTimers.delete(roomId); }
  }

  // Legacy method compatibility for tests / admin panel
  start() {
    // Intentionally no-op on startup: bots must NEVER auto-spam!
    console.log('[BotManager] Bots loaded in silent mode. Awaiting staff summon.');
  }

  stop() {
    for (const [rId, t] of this.autoTimers) clearTimeout(t);
    for (const [rId, q] of this.activeQuestions) if (q.timer) clearTimeout(q.timer);
    this.autoRooms.clear();
    this.autoTimers.clear();
    this.activeQuestions.clear();
  }

  get timer() {
    return this.autoRooms.size > 0;
  }
}

module.exports = { QuizBot: BotManager, BotManager };
