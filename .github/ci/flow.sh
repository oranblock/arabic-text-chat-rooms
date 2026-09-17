#!/usr/bin/env bash
# Emulator flow for com.ali.textchat. Invoked as ONE line from the workflow so it
# gets a real bash with state and pipefail. NOT `set -e`: probes return non-zero
# on purpose and must not abort the evidence sweep.
set -uo pipefail
cd "$GITHUB_WORKSPACE"
source .github/ci/lib.sh

FLOW="${FLOW:-smoke}"
APK_DIR="${APK_DIR:-apk}"
rc=0

# The app's SERVER_URL is http://localhost:3001. On the emulator that loops back
# to the emulator itself, so forward it to the ephemeral server on the runner.
adb reverse tcp:3001 tcp:3001 || echo "adb reverse failed"

# download-artifact leaves the .apk inside a directory — match files only.
APK="$(find "$APK_DIR" -type f -name '*.apk' | head -n1)"
echo "installing $APK"
adb install -r "$APK" || { _tg "❌ install failed"; exit 1; }

assert_package || rc=1
send_step "▶️ ${PACKAGE} — flow: ${FLOW}"

case "$FLOW" in
  diagnose)
    for act in ${ACTIVITIES}; do
      send_step "launch $act"
      launch_activity "$act"
      assert_running || rc=1
      shot "$(echo "$act" | tr '/.' '__')"
      home
    done
    ;;
  *)
    adb shell pm grant "$PACKAGE" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
    main="$(echo "$ACTIVITIES" | awk '{print $1}')"
    launch_activity "$main"
    assert_running || rc=1
    sleep 3
    shot "01-auth-screen"

    # Tap Guest Login (دخول كزائر, center approx x=540, y=1835 on 1080x2340 Pixel 5)
    send_step "tap guest login to enter chat"
    tap_by_text "دخول كزائر" 540 1835
    sleep 5
    shot "02-chatroom-main"

    # Open Rooms Dialog by tapping "قائمة الرومات" (RTL top-right: x=885, y=140)
    send_step "open rooms dialog"
    tap_by_text "قائمة الرومات" 885 140
    sleep 2
    shot "03-rooms-dialog"

    # Select room Baghdad (روم بغداد, item 2: x=540, y=1000)
    send_step "select room baghdad"
    tap_by_text "روم بغداد" 540 1000
    sleep 3
    shot "04-room-baghdad"

    # Tap a message in the chat list to trigger Quick-Mention
    send_step "tap message for quick mention"
    tap 600 540
    sleep 1
    shot "05-quick-mention"

    # Tap Online Users Drawer button (in RTL top bar: left side, x=70, y=140)
    send_step "open online users drawer"
    tap 70 140
    sleep 2
    shot "06-online-drawer-search"
    tap 800 500
    sleep 1

    # Open Floating Menu
    send_step "open floating menu"
    tap 70 240
    sleep 1
    shot "07-floating-menu"
    tap 70 240
    sleep 1

    # Test YouTube Player: initial seeded video
    send_step "verify youtube player in-chat"
    sleep 2
    shot "08-youtube-player-initial"

    # Update YouTube video via admin room-control API to P82XPloDtMc
    send_step "trigger youtube sync to P82XPloDtMc"
    curl -sf -X POST http://127.0.0.1:3001/api/admin/login -H "Content-Type: application/json" -d '{"name":"علي","password":"demo123"}' > /tmp/admin_auth.json || true
    ADMIN_TOK="$(grep -o '"token":"[^"]*' /tmp/admin_auth.json | cut -d'"' -f4)"
    if [ -n "$ADMIN_TOK" ]; then
      curl -sf -X POST http://127.0.0.1:3001/api/admin/room-control -H "Authorization: Bearer $ADMIN_TOK" -H "Content-Type: application/json" -d '{"roomId":"baghdad","youtubeId":"P82XPloDtMc"}' || true
    fi
    sleep 3
    shot "09-youtube-player-updated"

    # Toggle YouTube hide button in banner
    send_step "toggle youtube player hide/show"
    tap 980 140
    sleep 1
    shot "10-youtube-player-hidden"
    tap 980 140
    sleep 1
    shot "11-youtube-player-restored"

    home;                    shot "12-home"
    launch_activity "$main"; shot "13-resume"
    rotate_report;           shot "14-after-rotate"
    ;;
esac

send_step "✅ done (rc=$rc) — screenshots in artifacts/"
exit "$rc"
