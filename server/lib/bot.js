'use strict';
/**
 * Interactive Quiz & Entertainment Bot: "ست وداد 🤖"
 * Present in Iraqi text chat rooms ('games', 'iraq'), asks trivia and word-unscramble
 * riddles, checks incoming answers in real time, and awards points to winners.
 */

const QUESTIONS = [
  { q: 'ما هي عاصمة العراق في عهد الدولة العباسية؟', a: 'بغداد' },
  { q: 'رتب حروف الكلمة العراقية: (د - غ - ب - ا - د)', a: 'بغداد' },
  { q: 'نهر يمر في بغداد يقسمها إلى كرخ ورصافة؟', a: 'دجلة' },
  { q: 'ما هو اللقب الشهير لمدينة البصرة الفيحاء؟', a: 'الفيحاء' },
  { q: 'رتب حروف اسم المحافظة: (ل - ي - ب - ب - ا - ر)', a: 'اربيل' },
  { q: 'أكبر محافظات العراق من حيث المساحة؟', a: 'الانبار' },
  { q: 'في أي مدينة تقع المئذنة الملوية الشهيرة؟', a: 'سامراء' },
  { q: 'رتب حروف الأكلة العراقية الشعبية: (د - ل - م - ة - و)', a: 'دولمة' },
  { q: 'ما هو اللقب الشعبي لمدينة الموصل؟', a: 'أم الربيعين' },
  { q: 'رتب حروف النهر العظيم: (ف - ر - ا - ت)', a: 'الفرات' },
  { q: 'ما اسم بوابة بابل التاريخية الشهيرة؟', a: 'عشتار' },
  { q: 'رتب حروف السمك المشهور بالعراق: (م - ز - ك - و - ف)', a: 'مزكوف' }
];

class QuizBot {
  constructor(io, store, makeMessage) {
    this.io = io;
    this.store = store;
    this.makeMessage = makeMessage;
    this.activeQuestions = new Map(); // roomId -> { q, a }
    this.scores = new Map();           // userId -> points
    this.timer = null;
  }

  get user() {
    return {
      id: 'bot_widad',
      name: 'ست وداد 🤖',
      avatarUrl: 'https://api.dicebear.com/7.x/bottts/png?seed=Widad',
      rank: 'BOT',
      customHexColor: '#00E5FF',
      country: 'IQ',
      isMuted: false,
      isGhost: false,
      status: 'online',
      online: true
    };
  }

  start() {
    if (this.timer) return;
    // Auto-ask a question every 75s in games room
    this.timer = setInterval(() => {
      this.askQuestion('games');
    }, 75000);
    setTimeout(() => this.askQuestion('games'), 3000);
  }

  stop() {
    if (this.timer) { clearInterval(this.timer); this.timer = null; }
  }

  askQuestion(roomId = 'games') {
    const item = QUESTIONS[Math.floor(Math.random() * QUESTIONS.length)];
    this.activeQuestions.set(roomId, item);
    const text = `🎮 مسابقة ست وداد: ${item.q} (اكتب الجواب بالشات واربح 10 نقاط! 🏆)`;
    const msg = this.makeMessage(this.user, roomId, text);
    this.store.pushMessage(roomId, msg);
    this.io.to(roomId).emit('new_message', msg);
    return item;
  }

  checkAnswer(roomId, user, text) {
    const active = this.activeQuestions.get(roomId);
    if (!active || !text) return false;
    const norm = (s) => s.trim().toLowerCase().replace(/[\u064B-\u065Fأإآ]/g, (m) => m === 'ة' ? 'ه' : 'ا');
    const cleanUser = norm(text);
    const cleanAns = norm(active.a);
    if (cleanUser.includes(cleanAns) || cleanUser === cleanAns) {
      this.activeQuestions.delete(roomId);
      const pts = (this.scores.get(user.id) || 0) + 10;
      this.scores.set(user.id, pts);
      const reply = `🎉 كفووو يا @${user.name}! إجابتك صحيحة (${active.a}) وربحت 10 نقاط! (مجموع نقاطك: ${pts} 🏆)`;
      const msg = this.makeMessage(this.user, roomId, reply);
      this.store.pushMessage(roomId, msg);
      this.io.to(roomId).emit('new_message', msg);
      return true;
    }
    return false;
  }
}

module.exports = { QuizBot };
