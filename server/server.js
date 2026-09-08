/**
 * 🚀 High-Performance Real-Time Socket.io Server
 * Arabic Text Chat & Rooms System (سيرفر غرف الدردشة الجماعية والكتابية)
 */

const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const cors = require('cors');

const app = express();
app.use(cors());
app.use(express.json());

const server = http.createServer(app);
const io = new Server(server, {
  cors: { origin: '*', methods: ['GET', 'POST'] }
});

const PORT = process.env.PORT || 3000;

// In-Memory Moderation State
const bannedDevices = new Set();
const ghostUsers = new Set();
const userLastSent = new Map();

// Middleware: Check Hardware Device Ban
io.use((socket, next) => {
  const deviceHash = socket.handshake.query.deviceHash;
  if (deviceHash && bannedDevices.has(deviceHash)) {
    return next(new Error('DEVICE_BANNED: تم حظر عتاد هذا الجهاز نهائياً'));
  }
  socket.deviceHash = deviceHash;
  next();
});

io.on('connection', (socket) => {
  console.log(`[+] Client connected: ${socket.id} (Device: ${socket.deviceHash})`);

  // Join Room Event
  socket.on('join_room', ({ roomId, user }) => {
    socket.join(roomId);
    socket.userData = user;
    console.log(`User ${user.name} joined room: ${roomId}`);
    io.to(roomId).emit('user_joined', { user, onlineCount: io.sockets.adapter.rooms.get(roomId)?.size || 1 });
  });

  // Send Message Event (With Ghost Mode & Anti-Spam Filtering)
  socket.on('send_message', (msg) => {
    const userId = msg.senderId;
    const now = Date.now();

    // Anti-Spam: Rate Limit (1.2s minimum)
    const lastSent = userLastSent.get(userId) || 0;
    if (now - lastSent < 1200) {
      socket.emit('error_alert', { message: 'يرجى الانتظار ثانية واحدة بين كل رسالة وأخرى' });
      return;
    }
    userLastSent.set(userId, now);

    // Requirement 9: Ghost Mode Check
    if (ghostUsers.has(userId) || msg.isGhost) {
      // Send ONLY to the sender's socket! Others do not see it.
      socket.emit('new_message', { ...msg, isGhost: true });
      return;
    }

    // Normal Broadcast to everyone in the room
    io.to(msg.roomId).emit('new_message', msg);
  });

  // Admin Action: Toggle Ghost Mode on a User
  socket.on('admin_toggle_ghost', ({ targetUserId, enable }) => {
    if (enable) {
      ghostUsers.add(targetUserId);
    } else {
      ghostUsers.delete(targetUserId);
    }
    console.log(`[GHOST MODE] User ${targetUserId} -> ${enable ? 'ENABLED' : 'DISABLED'}`);
  });

  // Admin Action: Hardware Ban Device
  socket.on('admin_ban_device', ({ targetDeviceHash }) => {
    bannedDevices.add(targetDeviceHash);
    console.log(`[HARDWARE BAN] Device ${targetDeviceHash} has been BANNED permanently.`);
    
    // Disconnect any active sockets matching this device
    for (let [id, s] of io.of("/").sockets) {
      if (s.deviceHash === targetDeviceHash) {
        s.emit('force_disconnect', { reason: 'تم حظر جهازك نهائياً من قبل الإدارة' });
        s.disconnect(true);
      }
    }
  });

  // YouTube Synced Playback
  socket.on('sync_youtube', ({ roomId, videoId, status }) => {
    socket.to(roomId).emit('youtube_updated', { videoId, status });
  });

  socket.on('disconnect', () => {
    console.log(`[-] Client disconnected: ${socket.id}`);
  });
});

app.get('/health', (req, res) => {
  res.json({ status: 'ok', uptime: process.uptime(), bannedDevicesCount: bannedDevices.size });
});

server.listen(PORT, () => {
  console.log(`🎧 Real-time Chat Server running on port ${PORT}`);
});
