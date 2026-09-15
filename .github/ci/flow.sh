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
  *)  # smoke: launch, enter chat, exercise in-chat features, screenshot, rotate
    main="$(echo "$ACTIVITIES" | awk '{print $1}')"
    launch_activity "$main"
    assert_running || rc=1
    shot "auth-screen"

    # Tap Guest Login (x=540, y=1625 on 1080x2340 Pixel 5) to enter chat room
    send_step "tap guest login to enter chat"
    tap 540 1625
    sleep 4
    shot "chatroom-features"

    # Tap a message in the chat list to trigger Quick-Mention
    send_step "tap message for quick mention"
    tap 500 750
    sleep 1
    shot "quick-mention"

    # Tap Online Users Drawer button (top right: x=1010, y=140)
    send_step "open online users drawer"
    tap 1010 140
    sleep 2
    shot "online-drawer-search"
    back

    # Tap Profile button (x=880, y=140)
    send_step "open profile dialog"
    tap 880 140
    sleep 2
    shot "profile-vip-palette"
    back

    home;            shot "home"
    launch_activity "$main"; shot "resume"
    rotate_report;   shot "after-rotate"
    ;;
esac

send_step "✅ done (rc=$rc) — screenshots in artifacts/"
exit "$rc"
