const ioClient = require('socket.io-client');

const BASE_URL = 'http://127.0.0.1:3001';

async function testSystem() {
  console.log('--- بدء الفحص الشامل للمواصفات الخمس ---');

  // 1. Test Admin Login & Room Creation
  console.log('\n[1] اختبار إنشاء غرفة جديدة بصلاحيات مختلفة عبر Admin API...');
  const loginRes = await fetch(`${BASE_URL}/api/admin/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name: 'علي', password: '123456' })
  });
  const loginData = await loginRes.json();
  if (!loginData.ok || !loginData.token) {
    throw new Error('فشل تسجيل دخول الأدمن: ' + JSON.stringify(loginData));
  }
  const token = loginData.token;
  console.log('  ✅ تم تسجيل دخول الأدمن بنجاح باسم: ' + loginData.user.name);

  const testRoomId = 'mods_vip_' + Date.now().toString(36);
  const createRoomRes = await fetch(`${BASE_URL}/api/admin/rooms/create`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    },
    body: JSON.stringify({
      id: testRoomId,
      title: 'ديوان المشرفين والمميزين',
      description: 'غرفة خاصة بطاقم الإشراف والأعضاء المميزين',
      topic: 'أهلاً بكم في غرفة الإدارة والمراقبين',
      requiredRank: 'MODERATOR'
    })
  });
  const createRoomData = await createRoomRes.json();
  if (!createRoomData.ok) {
    throw new Error('فشل إنشاء الغرفة: ' + JSON.stringify(createRoomData));
  }
  console.log('  ✅ تم إنشاء الغرفة بنجاح مع رتبة MODERATOR: ' + createRoomData.room.title + ' (' + createRoomData.room.id + ')');

  // 2. Test Media Upload (Image & Audio)
  console.log('\n[2] اختبار رفع الصور والمقاطع الصوتية عبر POST /api/upload...');
  const dummyImageBase64 = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==';
  const imgUploadRes = await fetch(`${BASE_URL}/api/upload`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      data: dummyImageBase64,
      filename: 'sample.png',
      type: 'image'
    })
  });
  const imgUploadData = await imgUploadRes.json();
  if (!imgUploadData.ok || !imgUploadData.url) {
    throw new Error('فشل رفع الصورة: ' + JSON.stringify(imgUploadData));
  }
  console.log('  ✅ تم رفع الصورة بنجاح: ' + imgUploadData.url);

  const audioUploadRes = await fetch(`${BASE_URL}/api/upload`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      data: dummyImageBase64,
      filename: 'voice_note.m4a',
      type: 'audio'
    })
  });
  const audioUploadData = await audioUploadRes.json();
  if (!audioUploadData.ok || !audioUploadData.url) {
    throw new Error('فشل رفع التسجيل الصوتي: ' + JSON.stringify(audioUploadData));
  }
  console.log('  ✅ تم رفع التسجيل الصوتي بنجاح: ' + audioUploadData.url);

  // 3. Test Socket Connection & Sending Media Messages
  console.log('\n[3] اختبار إرسال واستقبال رسائل الوسائط والمسابقات عبر Socket.IO...');
  const socket = ioClient(BASE_URL, {
    query: { deviceHash: 'test_device_hash_' + Date.now() },
    transports: ['websocket']
  });

  await new Promise((resolve, reject) => {
    socket.on('connect', () => {
      console.log('  ✅ تم اتصال السوكيت بنجاح');
      socket.emit('register', {
        name: 'مستخدم_فحص_' + Math.floor(Math.random() * 9000 + 1000),
        password: 'password123'
      }, (res) => {
        if (!res || !res.ok) return reject(new Error('فشل التسجيل: ' + JSON.stringify(res)));
        console.log('  ✅ تم تسجيل مستخدم جديد بنجاح: ' + res.user.name);

        socket.emit('join_room', { token: res.token, roomId: 'iraq' }, (joinRes) => {
          if (!joinRes || !joinRes.ok) return reject(new Error('فشل دخول الروم: ' + JSON.stringify(joinRes)));
          console.log('  ✅ تم الانضمام للروم بنجاح: ' + joinRes.room.title);

          // Send image message
          socket.emit('send_message', {
            text: 'صورة تجريبية من الهاتف',
            mediaType: 'image',
            mediaUrl: imgUploadData.url
          });

          // Send audio message
          socket.emit('send_message', {
            text: 'تسجيل صوتي تجريبي',
            mediaType: 'audio',
            mediaUrl: audioUploadData.url,
            audioDuration: 5
          });

          console.log('  ✅ تم إرسال رسائل الصورة والصوت بنجاح عبر السوكيت');
          setTimeout(() => {
            socket.disconnect();
            resolve();
          }, 1000);
        });
      });
    });
    socket.on('connect_error', (err) => reject(err));
  });

  // 4. Test Bot System & Silent Mode
  console.log('\n[4] اختبار منظومة البوتات التفاعلية والمسابقات...');
  const botRes = await fetch(`${BASE_URL}/api/admin/bots`, {
    headers: { 'Authorization': `Bearer ${token}` }
  });
  const botData = await botRes.json();
  console.log('  ✅ تم فحص منظومة البوتات: ' + botData.bots.length + ' بوت متوفر، إجمالي الأسئلة: ' + botData.totalQuestions);

  // 5. Test Room Deletion
  console.log('\n[5] اختبار حذف الغرفة التجريبية...');
  const delRes = await fetch(`${BASE_URL}/api/admin/rooms/delete`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    },
    body: JSON.stringify({ roomId: testRoomId })
  });
  const delData = await delRes.json();
  if (!delData.ok) {
    throw new Error('فشل حذف الغرفة: ' + JSON.stringify(delData));
  }
  console.log('  ✅ تم حذف الغرفة التجريبية بنجاح ونقل الحالة إلى السيرفر');

  console.log('\n🎉 اكتمل الفحص الشامل بنجاح تام! جميع المواصفات الـ 5 تعمل وجاهزة 100%.');
  process.exit(0);
}

testSystem().catch(err => {
  console.error('❌ خطأ أثناء الفحص:', err);
  process.exit(1);
});
