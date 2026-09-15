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
  *)  # smoke: launch, prove alive, screenshot, background/resume, rotate
    main="$(echo "$ACTIVITIES" | awk '{print $1}')"
    launch_activity "$main"
    assert_running || rc=1
    shot "launch"
    home;            shot "home"
    launch_activity "$main"; shot "resume"
    rotate_report;   shot "after-rotate"
    ;;
esac

send_step "✅ done (rc=$rc) — screenshots in artifacts/"
exit "$rc"
