# Technical Specification: Iraqi Realtime Text Chat Platform & Native Android App

## 1. System Architecture Overview

The system is designed as a distributed, high-concurrency real-time group chat platform consisting of three major pillars:
1. **Native Android Application (`/android_app`)**: Built using Kotlin, Jetpack Compose, Coroutines, StateFlow, Koin, Coil, and Airbnb Lottie.
2. **Real-time Gateway & REST Server (`/server`)**: Built with Node.js, Express, Socket.IO 4.x, scrypt password hashing, and in-memory transactional JSON storage with atomic disk flush.
3. **Responsive Web Administration Dashboard (`/server/public/admin`)**: Single-page administrative console built with HTML5, CSS Variables, and vanilla ES6+ for maximum operational speed and zero external dependency risk.

---

## 2. Core Functional Pillars

### 2.1. Multi-Room & Hierarchical Role System
* **Rooms Engine**: Dynamically managed rooms with distinct configurations:
  - `id`: Unique string slug (e.g., `iraq`, `baghdad`, `mods_vip`).
  - `title`: UTF-8 Arabic title displayed in lists and headers.
  - `topic`: Real-time banner/topic displayed above chat bubbles.
  - `lockPublic`: Boolean flag to restrict message sending to staff only.
  - `lockPrivate`: Boolean flag to block private 1-on-1 chatting originating from this room.
  - `requiredRank`: Entry rank gatekeeper (`REGULAR` < `MEMBER` < `VIP` < `MODERATOR` < `OWNER`).
* **Hierarchy Shield**: Strict privilege enforcement ensuring lower-ranked users cannot kick, ban, mute, or demote higher-ranked users or equals.

### 2.2. Administration Dashboard & Abuse Defense
* **Endpoints**:
  - `POST /api/admin/login`: Secure staff authentication issuing cryptographic bearer tokens.
  - `GET /api/admin/data`: Consolidated live telemetry (online users, room states, banned devices/IPs, message log).
  - `POST /api/admin/rooms/create`: Dynamic chat room instantiation with rank barriers.
  - `POST /api/admin/rooms/delete`: Dynamic room deprecation with graceful occupant eviction to `#iraq`.
  - `POST /api/admin/user-action`: Real-time administrative actions (`mute`, `unmute`, `kick`, `ban_ip`, `ban_device`, `promote`, `demote`).
* **Abuse Protection (Red Team Hardened)**:
  - **Reverse Proxy Spoofing Check**: `getClientIp()` prioritizes `cf-connecting-ip`. `x-forwarded-for` is rejected unless the direct remote socket is strictly a loopback address (`127.0.0.1` / `::1`).
  - **Hardware Device Ban**: Blocks unique device fingerprints (`deviceHash`), mitigating churn from account recreation.
  - **Socket Exhaustion Protection**: Caps active sockets to 10 per IP with an unauthenticated disconnect timer (20s) and an automated 30s garbage collector.

### 2.3. Automated Trivia & Word-Scramble Bot (`QuizBot`)
* **Features**:
  - **Scrambled Word Engine**: Presents mixed-up Arabic letters; awards 10 points to the fastest correct answer.
  - **Silent Mode**: Prevents chat spam; bots only speak when explicitly summoned (`/quiz`) or via scheduled automated room intervals.
  - **Multi-Bot Assignment**: Supports multiple custom bots with dedicated names and avatars bound to specific rooms.
  - **JSON Importer**: Admin dashboard allows bulk-importing trivia questions and bot definitions via standardized JSON files.

### 2.4. Media Pipeline & Lottie Vector Animations
* **Media Uploads (`POST /api/upload`)**:
  - Base64 direct upload supporting images and voice notes up to 10MB.
  - Audio encoded in high-fidelity MPEG-4 AAC (`.m4a`) recorded via Android's `MediaRecorder`.
* **Lottie Vector Animations**:
  - Direct integration of `com.airbnb.android:lottie-compose:6.4.0`.
  - 16 bundled Google Noto Animated Emojis (Lottie JSON) under CC-BY 4.0 (`app/src/main/assets/lottie_emojis/`).
  - 90%+ bandwidth savings compared to animated GIFs with 60fps hardware-accelerated rendering on all screen densities.
  - Arabic and English token matching (e.g., `:fire:`, `:نار:`, `:heart:`, `:قلب:`).

---

## 3. Real-Time Socket.IO Protocol Specification

| Event Name | Direction | Payload Parameters | Description |
| :--- | :--- | :--- | :--- |
| `register` | Client -> Server | `{ name, password, avatarUrl, guest }` | Registers an account and assigns session token |
| `login` | Client -> Server | `{ name, password, token }` | Authenticates existing user or validates token |
| `join_room` | Client -> Server | `{ token, roomId }` | Joins a room, validates rank barrier, sends history |
| `send_message` | Client -> Server | `{ text, mediaType, mediaUrl, audioDuration }` | Broadcasts text or media message to current room |
| `private_send` | Client -> Server | `{ toUserId, text, mediaType, mediaUrl, audioDuration }` | Delivers encrypted 1-on-1 direct message |
| `error_alert` | Server -> Client | `{ message }` | Displays high-priority warning or violation toast |
| `system_message` | Server -> Client | `{ roomId, text, at }` | Room announcement or presence update |
| `rooms` | Server -> Client | `{ rooms: [...] }` | Broadcasts updated room list and online user counts |

---

## 4. Build & Deployment Runbook

### 4.1. Server Deployment (Node.js)
```bash
# 1. Install dependencies
cd server
npm install

# 2. Start production server
PORT=3001 DB_FILE=data/db.json node server.js
```

### 4.2. Android Release Build
```bash
# 1. Navigate to android app directory
cd android_app

# 2. Build optimized release APK
./gradlew :app:assembleRelease -x lint -x test

# 3. Output artifact location
# app/build/outputs/apk/release/app-release.apk
```

---

## 5. Artifact Verification
* **Release APK**: `/sdcard/Download/ali-chat-release.apk` (Size: 3.0 MB)
* **Local Test Runner**: `node server/test_complete_system.js` (All 5 verification suites passing)
