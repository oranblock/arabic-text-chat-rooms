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
    adb shell pm grant "$PACKAGE" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
    main="$(echo "$ACTIVITIES" | awk '{print $1}')"
    launch_activity "$main"
    assert_running || rc=1
    shot "01-auth-screen"

    # Tap Guest Login (x=540, y=1625 on 1080x2340 Pixel 5) to enter chat room
    send_step "tap guest login to enter chat"
    tap 540 1625
    sleep 4
    shot "02-chatroom-main"

    # Tap a message in the chat list to trigger Quick-Mention
    send_step "tap message for quick mention"
    tap 600 240
    sleep 1
    shot "03-quick-mention"

    # Tap Online Users Drawer button (in RTL top bar: x=70, y=90)
    send_step "open online users drawer"
    tap 70 90
    sleep 2
    shot "04-online-drawer-search"
    back

    # Tap Profile button (in RTL top bar: x=175, y=90)
    send_step "open profile dialog"
    tap 175 90
    sleep 2
    shot "05-profile-vip-palette"
    back

    home;            shot "06-home"
    launch_activity "$main"; shot "07-resume"
    rotate_report;   shot "08-after-rotate"
    ;;
esac

send_step "✅ done (rc=$rc) — screenshots in artifacts/"
exit "$rc"
