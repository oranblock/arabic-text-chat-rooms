'use strict';
/**
 * Tiny JSON-file store. Keeps the whole state in memory and writes it back to
 * data/db.json at most once per second, so the server survives restarts without
 * needing a native database driver on the VPS.
 */
const fs = require('fs');
const path = require('path');

const FILE = process.env.DB_FILE || path.join(__dirname, '..', 'data', 'db.json');
const HISTORY_PER_ROOM = 200;
const HISTORY_PRIVATE = 200;

const DEFAULT_ROOMS = [
  { id: 'iraq', title: 'ديوانية العراق', description: 'الغرفة العامة الرئيسية', topic: 'هلا بيكم بديوانية العراق 🇮🇶 احترموا بعض', youtubeId: 'jfKfPfyJRdk', youtubeTitle: 'موسيقى هادئة - ديوانية العراق 🎵' },
  { id: 'baghdad', title: 'روم بغداد', description: 'سوالف أهل بغداد', topic: '' },
  { id: 'basra', title: 'روم البصرة', description: 'أهل البصرة والجنوب', topic: '' },
  { id: 'games', title: 'مسابقات ست وداد', description: 'مسابقات وألغاز يومية', topic: 'رتب الحروف واربح نقاط 🏆' },
  { id: 'songs', title: 'شيلات وأغاني', description: 'يوتيوب وأغاني عراقية', topic: '', youtubeId: 'jfKfPfyJRdk', youtubeTitle: 'أغاني وشيلات عراقية 🎶' }
];

function emptyState() {
  return {
    users: {},            // id -> user
    names: {},            // lower(name) -> id
    tokens: {},           // token -> id
    rooms: Object.fromEntries(DEFAULT_ROOMS.map(r => [r.id, { ...r, lockPublic: false, lockPrivate: false }])),
    messages: {},         // roomId -> [message]
    privates: {},         // "a|b" -> [message]
    bannedDevices: {},    // deviceHash -> { by, at }
    deviceAccounts: {},   // deviceHash -> [userId]
    seq: 1
  };
}

let state = emptyState();
try {
  if (fs.existsSync(FILE)) {
    const loaded = JSON.parse(fs.readFileSync(FILE, 'utf8'));
    state = { ...emptyState(), ...loaded };
    for (const r of DEFAULT_ROOMS) {
      if (!state.rooms[r.id]) {
        state.rooms[r.id] = { ...r, lockPublic: false, lockPrivate: false };
      } else if (!state.rooms[r.id].youtubeId && r.youtubeId) {
        state.rooms[r.id].youtubeId = r.youtubeId;
        state.rooms[r.id].youtubeTitle = r.youtubeTitle;
      }
    }
  }
} catch (e) {
  console.error('[store] could not read db, starting fresh:', e.message);
}

let timer = null;
function save() {
  if (timer) return;
  timer = setTimeout(flush, 1000);
}

function flush() {
  if (timer) { clearTimeout(timer); timer = null; }
  fs.mkdirSync(path.dirname(FILE), { recursive: true });
  const tmp = FILE + '.tmp';
  fs.writeFileSync(tmp, JSON.stringify(state));
  fs.renameSync(tmp, FILE);
}

function nextId(prefix) {
  const id = `${prefix}${state.seq++}`;
  save();
  return id;
}

function pushMessage(roomId, msg) {
  const list = state.messages[roomId] || (state.messages[roomId] = []);
  list.push(msg);
  if (list.length > HISTORY_PER_ROOM) list.splice(0, list.length - HISTORY_PER_ROOM);
  save();
}

function pairKey(a, b) { return [a, b].sort().join('|'); }

function pushPrivate(a, b, msg) {
  const key = pairKey(a, b);
  const list = state.privates[key] || (state.privates[key] = []);
  list.push(msg);
  if (list.length > HISTORY_PRIVATE) list.splice(0, list.length - HISTORY_PRIVATE);
  save();
}

module.exports = {
  get state() { return state; },
  save, flush, nextId, pushMessage, pushPrivate, pairKey, FILE
};
