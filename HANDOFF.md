# Handoff — ديوانية العراق (Iraqi Group Text-Chat)

Complete delivery guide for the native Android chat app and its realtime server.
Everything a new developer or the client needs to build, run, deploy, and extend
the project.

---

## 1. What this is

A native Android group text-chat app styled after iqchat.top (a BoomChat
deployment) with an Iraqi identity — IQ flag, Iraqi dialect, default room
"ديوانية العراق". Kotlin + Jetpack Compose (Material 3), right-to-left, talking
to a Node.js + Socket.io realtime server over WebSockets.

- **Android app:** `android_app/` — Kotlin 2.0, Compose, socket.io-client, Coil
  (svg + gif), Firebase Cloud Messaging (dormant until configured).
- **Server:** `server/` — Node 20, Socket.io 4, Express, JSON-file store, scrypt
  password hashing, optional FCM push.

Package id: `com.ali.textchat`. Default server URL: `http://localhost:3001`
(overridable in `AppConfig.kt`).

---

## 2. Repositories

| repo | visibility | purpose |
| :--- | :--- | :--- |
| `oranblock/arabic-text-chat-rooms` | **private** | full source (app + server) |
| `oranblock/ali-chat-runner` | **public** | CI only — builds + emulator-tests the private source, never publishes it |

The source stays private (job not yet delivered/paid). The public runner checks
it out with a token at run time — the oranblock/Android-test-harness pattern.

---

## 3. The 9 client conditions — status

All nine implemented. Audited against `README.md` / `TEXT_CHAT_PLAN.md`.

| # | Condition | Where |
| :--- | :--- | :--- |
| 1 | Royal-blue top bar, 5 buttons (notifications, private inbox, requests, profile, people); rooms via title tap; Logout inside Profile | `ChatRoomScreen.kt`, `Color.kt` (`BcHeaderStart/End`) |
| 2 | Rounded avatars + bubbles, time far-left `HH:mm`, name above | `MessageBubble.kt` |
| 3 | Rank colors (owner gold, mod silver/violet, VIP diamond) + VIP picks own color | `MessageBubble.kt` `skinFor`, `ProfileDialog` |
| 4 | Quick-mention on tap → `@name:` | `MessageBubble.kt` `onUserMention` |
| 5 | Online drawer grouped by rank + **search** | `OnlineUsersDrawer.kt` |
| 6 | In-chat YouTube player + room sync | `YouTubeInChatPlayer.kt`, server `sync_youtube` |
| 7 | Anti-spam (2s gap + dedupe) + auto-mute new members | `server.js` `send_message`, register `isMuted:!isFirst` |
| 8 | Hardware device ban (hashed ANDROID_ID) | `DeviceId.kt`, server `bannedDevices` |
| 9 | **Silent** ghost mode — target sees own messages delivered, nobody else does, no marker (keeps troublemaker calm, no rage-quit) | `server.js` `send_message` ghost branch, `MessageBubble.kt` |

Extra shipped: GIF emoticons (`:code:` inline + picker), FCM push scaffolding,
private-message inbox, friend requests.

---

## 4. Run it locally

### Server
```sh
cd server
npm install
PORT=3001 DB_FILE=data/db.json node server.js
curl http://localhost:3001/health   # {"status":"ok",...}
```
First account ever registered becomes **OWNER**. New members join **auto-muted**
until a moderator approves them.

### Android app on a device/emulator
```sh
adb reverse tcp:3001 tcp:3001       # maps device localhost:3001 -> laptop server
cd android_app
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
`AppConfig.SERVER_URL` is `http://localhost:3001`; with `adb reverse` it reaches
the laptop server. For a real deployment set it to your VPS domain (wss via nginx).

### Headless server test
```sh
cd server
DB_FILE=data/test.json PORT=3013 node server.js &
URL=http://localhost:3013 node test_flow.js     # expect 23/23 PASS
```

---

## 5. Deploy the server to a VPS (Ubuntu 22.04/24.04)

```sh
cd server
bash deploy.sh                       # HTTP only, service on 127.0.0.1:3000
# with domain + nginx WebSocket proxy:
DOMAIN=chat.example.com bash deploy.sh
# HTTPS:
apt-get install -y certbot python3-certbot-nginx
certbot --nginx -d chat.example.com
```
Installs Node 20 + nginx, creates the `alichat` systemd service and user, rsyncs
to `/opt/alichat/server`, `npm ci`, enables the service. Config in
`/opt/alichat/server/.env` (from `.env.example`). Data persists to `DB_FILE`
(JSON). Service control: `systemctl {status,restart} alichat`,
`journalctl -u alichat -f`.

Files: `deploy.sh`, `alichat.service`, `nginx.conf.sample`, `.env.example`.

---

## 6. Enable FCM push (optional)

Both sides are wired and dormant. Push works only after BOTH steps.

**App:** create a Firebase project, add an Android app `com.ali.textchat`, drop
`google-services.json` into `android_app/app/`, add the `com.google.gms.google-services`
Gradle plugin (top-level `apply false` + app module `id`), rebuild. Without the
file, `PushService`/token registration no-op and the app runs normally.

**Server:** Firebase Console → Service accounts → generate key, save on the VPS,
set `FCM_SERVICE_ACCOUNT=/opt/alichat/server/fcm.json` in `.env`, restart. Offline
users then get push for private messages and admin broadcasts.

---

## 7. CI — public runner (`ali-chat-runner`)

`Actions → Android CI (private source) → Run workflow` (inputs `ref`, `api_level`,
`flow`). It builds the debug APK from the private source, starts an **ephemeral**
chat server on the runner + seeds a demo room (`server/seed_ci.js`), `adb reverse
tcp:3001` so the app reaches it, boots an emulator, installs, launches, screenshots
each step, and reports status + screenshots to Telegram.

Secrets required on `ali-chat-runner`:
- `SOURCE_REPO_TOKEN` — PAT that can read the private source (fine-grained
  Contents:read on `arabic-text-chat-rooms`, or classic `repo`). **Required.**
- `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID` — optional (skip = no Telegram, run
  still passes).

Local dispatch:
```sh
gh workflow run "Android CI (private source)" --repo oranblock/ali-chat-runner \
  -f ref=main -f api_level=34 -f flow=smoke
gh run watch <run-id> --repo oranblock/ali-chat-runner
```
Flows: `smoke` (launch, prove alive, background/resume, rotate) or `diagnose`
(launch every activity, screenshot each).

> **Verified:** The runner's `android-ci.yml` is active and verified green
> (run `34951628062`). Both `build` (assembleDebug) and `emulator-test` (ephemeral
> Node.js server + emulator boot + smoke flow + 4 screenshots) passed cleanly.

The private repo also carries its own in-repo `android-ci.yml` for owner-only runs
(uses the checked-out source directly, no token).

---

## 8. Project layout

```
client_ali_text_chat/
├── HANDOFF.md                 # this file
├── README.md                  # Arabic client-facing pitch (9 conditions, costs, warranty)
├── TEXT_CHAT_PLAN.md/.json    # execution plan
├── android_app/               # Kotlin + Compose app
│   └── app/src/main/java/com/ali/textchat/
│       ├── MainActivity.kt
│       ├── data/              # ChatSocket, Session, AppConfig, DeviceId, PushService
│       ├── model/             # ChatModels (UserRank, ChatUser, ChatMessage, ChatRoom, PmThread)
│       └── ui/                # screens/ components/ theme/ util/
├── server/                    # Node + Socket.io
│   ├── server.js              # all events
│   ├── lib/                   # store, auth, filter, push
│   ├── test_flow.js           # 23-check e2e
│   ├── seed_ci.js             # CI demo seed
│   ├── deploy.sh alichat.service nginx.conf.sample .env.example
└── .github/                   # in-repo CI (workflows/android-ci.yml, ci/flow.sh, ci/lib.sh)
```

---

## 9. Delivery checklist

- [x] Native Android app, 9 conditions, builds clean (`assembleDebug`)
- [x] Realtime server, `test_flow.js` 23/23
- [x] VPS deploy (systemd + nginx + certbot instructions)
- [x] FCM push scaffolding (app + server, dormant until keys)
- [x] GIF emoticons
- [x] Private repo (source) + public CI runner (harness split)
- [x] Runner `android-ci.yml` indexed, first green run passed (build + ephemeral server + emulator smoke + 4 screenshots)
- [ ] Client: register Google Play Console ($25 one-time), pick VPS, add FCM keys
- [ ] 60-day warranty support window (per README)

---

## 10. Key facts / gotchas

- Device ban survives reinstall and new accounts; resets only on factory reset —
  Android does not allow a truly permanent hardware ban.
- Ghost mode is deliberately invisible to the target — do not add any UI marker.
- The emulator action runs each `script:` line as its own `sh -c`; keep `script:`
  one line invoking a checked-in bash file. Flows run `set -uo pipefail`, never `-e`.
- `AppConfig.SERVER_URL` is the single place to point the app at prod vs local.
- JSON-file store is fine for launch; to scale, only `server/lib/store.js` changes
  — the Socket.io event API stays the same.
