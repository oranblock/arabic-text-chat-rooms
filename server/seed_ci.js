'use strict';
/**
 * Seeds the ephemeral CI server with a demo Iraqi room so the emulator opens to
 * a populated screen instead of an empty one. Run after the server is up:
 *   PORT=3001 DB_FILE=data/ci.json node server.js &   then   node seed_ci.js
 * Safe to run twice: the OWNER is whoever registered first (a fresh ci.json);
 * on a reused db the owner name already exists and the seed exits quietly.
 */
const { io } = require('socket.io-client');
const URL = process.env.URL || 'http://localhost:3001';
const wait = (ms) => new Promise(r => setTimeout(r, ms));
const connect = (d) => io(URL, { query: { deviceHash: d }, forceNew: true, transports: ['websocket', 'polling'] });
const rpc = (s, e, p) => new Promise(r => s.timeout(4000).emit(e, p, (err, res) => r(err ? { ok: false } : res)));

(async () => {
  const owner = connect('ci-owner');
  const ali = await rpc(owner, 'register', { name: 'علي', password: 'demo123' });
  if (!ali.ok) { console.log('owner exists, seed skipped'); process.exit(0); }
  await rpc(owner, 'join_room', { token: ali.token, roomId: 'iraq' });
  await rpc(owner, 'send_message', { text: 'هلا بيكم بديوانية العراق 🌹 احترموا بعض ولا سب ولا اعلانات' });

  const members = [
    ['حسوني', 'شلونكم شباب شكو ماكو 😎'],
    ['ام حسن', 'مساء الورد على الكل 🌹'],
    ['بوب العراق', 'منو يعرف يشغل يوتيوب بالروم؟']
  ];
  for (const [name, text] of members) {
    const s = connect('ci-' + name);
    const r = await rpc(s, 'register', { name, password: 'demo123' });
    if (r.ok) {
      await rpc(owner, 'mod_action', { action: 'unmute', targetUserId: r.user.id }); // owner approves
      await rpc(s, 'join_room', { token: r.token, roomId: 'iraq' });
      await rpc(s, 'send_message', { text });
    }
    s.close();
    await wait(150);
  }
  await wait(400);
  console.log('seeded demo room');
  process.exit(0);
})();
