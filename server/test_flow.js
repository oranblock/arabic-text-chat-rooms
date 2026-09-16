'use strict';
/**
 * Headless end-to-end test of server.js. Spawns three clients (owner, member,
 * spammer) and checks accounts, auto-mute, moderation, filter, anti-spam,
 * ghost mode, private messages, device ban and persistence.
 * Run: node test_flow.js   (server must run with DB_FILE=data/test.json on URL's port)
 */
const { io } = require('socket.io-client');
const URL = process.env.URL || 'http://localhost:3010';

const wait = (ms) => new Promise(r => setTimeout(r, ms));
function connect(deviceHash) {
  return io(URL, { query: { deviceHash }, forceNew: true, transports: ['websocket', 'polling'] });
}
function rpc(sock, event, payload) {
  return new Promise((resolve) => sock.timeout(4000).emit(event, payload, (err, res) => resolve(err ? { ok: false, error: 'timeout' } : res)));
}

let pass = 0, failed = 0;
const check = (name, cond) => { console.log((cond ? 'PASS ' : 'FAIL ') + name); cond ? pass++ : failed++; };

(async () => {
  const owner = connect('dev-owner');
  const member = connect('dev-member');
  const spammer = connect('dev-spam');
  const memberMsgs = [];
  member.on('new_message', m => memberMsgs.push(m));

  const uniq = Date.now();
  const rOwner = await rpc(owner, 'register', { name: 'المالك' + uniq, password: 'pass123' });
  check('owner register', rOwner.ok);
  check('first account is OWNER', rOwner.user && rOwner.user.rank === 'OWNER');
  check('owner not muted', rOwner.user && rOwner.user.isMuted === false);

  const rMember = await rpc(member, 'register', { name: 'عضو' + uniq, password: 'pass123' });
  check('member register', rMember.ok);
  check('new member auto-muted', rMember.user && rMember.user.isMuted === true);

  const rSpam = await rpc(spammer, 'register', { name: 'سبام' + uniq, password: 'pass123' });
  check('spammer register', rSpam.ok);

  const dup = await rpc(member, 'register', { name: 'المالك' + uniq, password: 'x123' });
  check('duplicate name rejected', dup.ok === false);

  const jOwner = await rpc(owner, 'join_room', { token: rOwner.token, roomId: 'iraq' });
  check('owner joins + gets history', jOwner.ok && Array.isArray(jOwner.messages));
  check('owner joins gets youtubeId', jOwner.room && jOwner.room.youtubeId === 'jfKfPfyJRdk');
  const jMember = await rpc(member, 'join_room', { token: rMember.token, roomId: 'iraq' });
  const jSpam = await rpc(spammer, 'join_room', { token: rSpam.token, roomId: 'iraq' });
  check('member + spammer join', jMember.ok && jSpam.ok);

  const ytPromise = new Promise(resolve => member.once('youtube_updated', resolve));
  await rpc(owner, 'sync_youtube', { videoId: 'NEW_VID_123', videoTitle: 'أغنية جديدة' });
  const ytEvent = await Promise.race([ytPromise, wait(1500).then(() => null)]);
  check('sync_youtube broadcasts to room members', ytEvent && ytEvent.videoId === 'NEW_VID_123');

  member.emit('send_message', { text: 'مرحبا انا جديد' });
  await wait(300);
  check('muted member message blocked', !memberMsgs.some(m => m.text === 'مرحبا انا جديد'));

  const un = await rpc(owner, 'mod_action', { action: 'unmute', targetUserId: rMember.user.id });
  check('owner unmutes member', un.ok);
  await wait(100);
  member.emit('send_message', { text: 'شكراً صرت مفعل' });
  await wait(300);
  check('unmuted member delivered', memberMsgs.some(m => m.text === 'شكراً صرت مفعل'));

  member.emit('send_message', { text: 'زوروا موقعي example.com' });
  await wait(300);
  check('link blocked', !memberMsgs.some(m => m.text.includes('example.com')));

  member.emit('send_message', { text: 'رقمي 07701234567' });
  await wait(300);
  check('phone blocked', !memberMsgs.some(m => m.text.includes('07701234567')));

  const before = memberMsgs.length;
  member.emit('send_message', { text: 'رسالة سريعة' });
  member.emit('send_message', { text: 'رسالة سريعة' });
  await wait(500);
  check('anti-spam limits burst to <=1', (memberMsgs.length - before) <= 1);
  await rpc(owner, 'mod_action', { action: 'unmute', targetUserId: rMember.user.id });

  await rpc(owner, 'mod_action', { action: 'unmute', targetUserId: rSpam.user.id });
  await rpc(owner, 'mod_action', { action: 'ghost', targetUserId: rSpam.user.id });
  await wait(100);
  spammer.emit('send_message', { text: 'رسالة الشبح لا تظهر للاخرين' });
  await wait(400);
  check('ghost message hidden from others', !memberMsgs.some(m => m.text.includes('الشبح لا تظهر')));

  const pm = await rpc(member, 'private_send', { toUserId: rOwner.user.id, text: 'خاص للمالك' });
  check('private message sent', pm.ok);
  const ph = await rpc(owner, 'private_history', { withUserId: rMember.user.id });
  check('private history has message', ph.ok && ph.messages.some(m => m.text === 'خاص للمالك'));

  const colDenied = await rpc(member, 'update_profile', { customHexColor: '#ff0000' });
  check('name color denied for regular', colDenied.ok === false);
  await rpc(owner, 'mod_action', { action: 'promote', targetUserId: rMember.user.id, rank: 'VIP_DIAMOND' });
  const colOk = await rpc(member, 'update_profile', { customHexColor: '#ff0000' });
  check('name color allowed after VIP', colOk.ok && colOk.user.customHexColor === '#ff0000');

  await rpc(owner, 'mod_action', { action: 'ban_device', targetUserId: rSpam.user.id });
  await wait(300);
  const banned = connect('dev-spam');
  const bannedErr = await new Promise(res => { banned.on('connect_error', e => res(e.message)); setTimeout(() => res('no-error'), 2500); });
  check('banned device cannot reconnect', bannedErr.includes('DEVICE_BANNED'));
  banned.close();

  const alt = await rpc(owner, 'device_accounts', { targetUserId: rSpam.user.id });
  check('device_accounts lists alts', alt.ok && alt.accounts.length >= 1);

  const staffRes = await rpc(owner, 'list_staff', {});
  check('list_staff returns staff members', staffRes.ok && staffRes.staff.length >= 1);

  const unbanRes = await rpc(owner, 'mod_action', { action: 'unban_device', deviceHash: 'dev-spam' });
  check('unban_device succeeds', unbanRes.ok);

  const quizProm = new Promise(resolve => member.once('new_message', m => resolve(m)));
  await rpc(owner, 'mod_action', { action: 'trigger_quiz' });
  const qMsg = await Promise.race([quizProm, wait(1500).then(() => null)]);
  check('quiz bot asks question', qMsg && qMsg.senderName.includes('ست وداد'));

  // Standalone Web Admin Dashboard & REST API
  const adminPageRes = await fetch(`${URL}/admin`);
  check('web admin panel reachable at /admin', adminPageRes.status === 200);

  const adminLoginRes = await fetch(`${URL}/api/admin/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name: 'المالك' + uniq, password: 'pass123' })
  });
  const adminAuth = await adminLoginRes.json();
  check('web admin API login succeeds', adminAuth.ok && adminAuth.token);

  const bulkMuteRes = await fetch(`${URL}/api/admin/bulk-action`, {
    method: 'POST',
    headers: { 'Authorization': 'Bearer ' + adminAuth.token, 'Content-Type': 'application/json' },
    body: JSON.stringify({ action: 'mute', userIds: [rMember.user.id, rSpam.user.id] })
  });
  const bulkMuteData = await bulkMuteRes.json();
  check('web admin bulk-action mutes multiple users', bulkMuteData.ok && bulkMuteData.modified === 2);

  const bulkUnmuteRes = await fetch(`${URL}/api/admin/bulk-action`, {
    method: 'POST',
    headers: { 'Authorization': 'Bearer ' + adminAuth.token, 'Content-Type': 'application/json' },
    body: JSON.stringify({ action: 'unmute', userIds: [rMember.user.id, rSpam.user.id] })
  });
  const bulkUnmuteData = await bulkUnmuteRes.json();
  check('web admin bulk-action unmutes multiple users', bulkUnmuteData.ok && bulkUnmuteData.modified === 2);

  // In-chat admin slash commands
  const cmdSysMsg = new Promise(resolve => owner.once('system_message', m => resolve(m)));
  owner.emit('send_message', { text: '/topic اهلا بكم' });
  const cmdEvt = await Promise.race([cmdSysMsg, wait(1500).then(() => null)]);
  check('in-chat /topic command executes with feedback', cmdEvt && cmdEvt.text.includes('اهلا بكم'));

  owner.emit('send_message', { text: 'رسالة تبقى بالسجل' });
  await wait(300);
  const rejoin = await rpc(owner, 'join_room', { token: rOwner.token, roomId: 'iraq' });
  check('history persisted across rejoin', rejoin.ok && rejoin.messages.some(m => m.text === 'رسالة تبقى بالسجل'));

  console.log(`\nRESULT: ${pass} passed, ${failed} failed`);
  owner.close(); member.close(); spammer.close();
  process.exit(failed === 0 ? 0 : 1);
})();
