# Handoff — Bots content + VPS access (ديوانية العراق)

For the next agent/dev. Everything to (a) reach the live VPS, (b) add/expand bots
and quiz questions, (c) add bot avatars, (d) push changes live.

---

## 1. Live VPS

- **Host:** `root@192.236.249.134` (Ubuntu 24.04, Node 18, npm 9).
- **SSH:** key-based (an ed25519 key is already authorized). From the dev box:
  ```sh
  ssh root@192.236.249.134
  ```
  If a new machine needs access, append its public key to
  `~/.ssh/authorized_keys` on the VPS.
- **App dir:** `/opt/alichat/server`
- **Service:** systemd unit `alichat` (port 3000, `Restart=always`).
  ```sh
  systemctl status alichat
  systemctl restart alichat
  journalctl -u alichat -f          # live logs
  ```
- **Public endpoints:**
  - Health: `http://192.236.249.134:3000/health`
  - Admin dashboard: `http://192.236.249.134:3000/admin/`
  - Bot avatars: `http://192.236.249.134:3000/uploads/bots/<id>.webp`
- **Data files on VPS:** `/opt/alichat/server/data/` — `db.json` (users/rooms/messages),
  `bots.json` (bots + questions). Ports 80/443 are used by another service, so the
  app runs on `:3000` directly (HTTP). HTTPS later = domain + nginx + certbot.

The Android app points here via `android_app/.../data/AppConfig.kt`
(`DEFAULT_SERVER_URL = "http://192.236.249.134:3000"`).

---

## 2. Repo

- Private source: `oranblock/arabic-text-chat-rooms` (branch `main`). Commit +
  `git push origin main` after edits. Bot files live at:
  - `server/data/bots.json` — bots + questions (git-tracked via `git add -f`; the
    `data/` dir is otherwise gitignored).
  - `server/public/uploads/bots/*.webp` — bot avatars (also `git add -f`).

---

## 3. bots.json schema

```json
{
  "bots": [
    {
      "id": "bot_food",                 // unique, snake_case, prefix bot_
      "name": "طباخ الديوان 🍲",         // shown in chat/list
      "avatarUrl": "http://192.236.249.134:3000/uploads/bots/bot_food.webp",
      "rank": "BOT",
      "customHexColor": "#F59E0B",
      "role": "cuisine",                // free label / category grouping
      "greeting": "…"                    // sent when summoned
    }
  ],
  "questions": [
    { "category": "cuisine", "q": "…؟", "a": "الجواب", "points": 10 }
  ]
}
```

- **Answer matching** is by the `a` string (server `checkAnswer` in
  `server/lib/bot.js`). Keep answers short, one canonical form. `points` 10
  default; harder = 15–20.
- `category` links questions to a theme; a summoned bot asks from the shared pool.
- Current: **10 bots, 195 questions** (16–30 questions per bot across all categories).

### Add more content (recommended flow)
1. Edit `server/data/bots.json` locally — append to `bots` and/or `questions`.
2. Validate: `python3 -c "import json;d=json.load(open('server/data/bots.json'));print(len(d['bots']),len(d['questions']))"`
3. Upload + restart:
   ```sh
   scp server/data/bots.json root@192.236.249.134:/opt/alichat/server/data/bots.json
   ssh root@192.236.249.134 'systemctl restart alichat'
   ```
4. Commit: `git add -f server/data/bots.json && git commit && git push`.

### Or hot-import via admin API (no restart)
`POST http://192.236.249.134:3000/api/admin/bots/import` with a staff Bearer
token and body `{ "bots":[...], "questions":[...] }` (merges into the pool).
Get the token from `POST /api/admin/login` `{name,password}` (a staff account;
first account ever registered is OWNER).

---

## 4. Bot avatars

- Source any square image; crop + resize to 256px WebP (keeps things light):
  ```sh
  ffmpeg -y -i in.jpg -vf "crop='min(iw,ih)':'min(iw,ih)',scale=256:256" -q:v 80 \
    server/public/uploads/bots/<bot_id>.webp
  ```
- Set the bot's `avatarUrl` to `http://192.236.249.134:3000/uploads/bots/<bot_id>.webp`.
- Upload: `scp server/public/uploads/bots/<bot_id>.webp root@192.236.249.134:/opt/alichat/server/public/uploads/bots/`
- Verify: `curl -s -o /dev/null -w '%{http_code}' http://192.236.249.134:3000/uploads/bots/<bot_id>.webp` → `200`.

---

## 5. Running / summoning bots (admin dashboard)

Bots are **silent by default** — staff summon them per room:
- `POST /api/admin/bots/assign` `{roomId, botId, auto, autoSec}` — bind a bot to a
  room; `auto:true, autoSec:60` = ask a question every 60s.
- `POST /api/admin/bots/summon` `{roomId, botId}` — attach a bot.
- Immediate-question trigger endpoints also exist (`/api/admin/bots`).
- Scores show in the app's scoreboard (trophy button) and `/api/admin` metrics.

All bot logic: `server/lib/bot.js` (BotManager). Admin routes:
`server/lib/admin_api.js`.

---

## 6. Quick checklist for the next agent

- [ ] `ssh root@192.236.249.134` works.
- [ ] Add bots/questions to `server/data/bots.json` (validate JSON).
- [ ] Add 256px WebP avatars under `server/public/uploads/bots/`.
- [ ] `scp` both to the VPS, `systemctl restart alichat`, verify `/health` + avatar `200`.
- [ ] `git add -f` the bot files, commit, `git push origin main`.
- [ ] Confirm in `/admin/` (mobile too) and in the app on summon.

---

## 7. Current bots (10)

widad (trivia), scramble (كلمات), abu_haider (أمثال ☕), deen (ديني 🕌),
shaer (شعر 📜), food (طبخ 🍲), kora (كورة ⚽), songs (شيلات 🎵), riddle (ألغاز 🧠),
math (رياضيات 🔢). All have avatars under `/uploads/bots/`.
