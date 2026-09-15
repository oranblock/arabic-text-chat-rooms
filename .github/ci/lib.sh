# Emulator-driving helpers, sourced by flow.sh. Runs under a real bash (the file
# has a shebang), so multi-line constructs and pipefail are safe here — unlike
# the emulator action's inline `script:`, which is one `sh -c` per line.
#
# Never `set -e` in a flow: pidof/grep probes legitimately return non-zero and
# must not abort the evidence sweep. flow.sh uses `set -uo pipefail`.

: "${PACKAGE:?PACKAGE required}"
ART=artifacts
mkdir -p "$ART"
STEP=0

_tg() {  # _tg <text> [image]
  [ -n "${TELEGRAM_BOT_TOKEN:-}" ] && [ -n "${TELEGRAM_CHAT_ID:-}" ] || return 0
  if [ -n "${2:-}" ] && [ -f "${2:-}" ]; then
    curl -sf -F chat_id="$TELEGRAM_CHAT_ID" -F caption="$1" -F photo=@"$2" \
      "https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/sendPhoto" >/dev/null || echo "telegram photo failed"
  else
    curl -sf --data-urlencode "text=$1" --data chat_id="$TELEGRAM_CHAT_ID" \
      "https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/sendMessage" >/dev/null || echo "telegram send failed"
  fi
}

shot() {  # shot <label>
  STEP=$((STEP + 1))
  local f
  f="$ART/$(printf '%02d' "$STEP")-$1.png"
  adb exec-out screencap -p > "$f" 2>/dev/null
  echo "screenshot $f"
  _tg "🧪 step $STEP: $1" "$f"
}

send_step() { echo "== $* =="; _tg "$*"; }

launch_activity() {  # launch_activity <pkg/.Activity>
  adb shell am start -n "$1" -a android.intent.action.MAIN >/dev/null 2>&1
  sleep 3
}

home() { adb shell input keyevent KEYCODE_HOME; sleep 1; }
back() { adb shell input keyevent KEYCODE_BACK; sleep 1; }
tap()  { adb shell input tap "$1" "$2"; sleep 1; }

assert_package() {  # fail loud if the app is not installed (applicationIdSuffix trap)
  echo "installed 3rd-party packages:"
  adb shell pm list packages -3
  if adb shell pm list packages -3 | grep -qF "package:$PACKAGE"; then
    echo "OK: $PACKAGE installed"
  else
    echo "FAIL: $PACKAGE not installed"; _tg "❌ $PACKAGE not installed"; return 1
  fi
}

assert_running() {  # pidof matches NAME only; fall back to ps
  if adb shell pidof "$PACKAGE" >/dev/null 2>&1 || adb shell ps -A 2>/dev/null | grep -qF " $PACKAGE"; then
    echo "OK: $PACKAGE running"; return 0
  fi
  echo "FAIL: $PACKAGE not running"
  adb shell dumpsys activity activities | grep -i mResumedActivity || true
  _tg "❌ $PACKAGE not running after launch"
  return 1
}

rotate_report() {  # rotation is a request, not a result — report what actually happened
  local before after
  before=$(adb shell wm size | tr -d '\r')
  adb shell settings put system accelerometer_rotation 0 >/dev/null 2>&1
  adb shell settings put system user_rotation 1 >/dev/null 2>&1
  sleep 2
  after=$(adb shell wm size | tr -d '\r')
  if [ "$before" = "$after" ]; then echo "rotation UNCHANGED (orientation-locked): $before"
  else echo "rotation applied: $before -> $after"; fi
  adb shell settings put system user_rotation 0 >/dev/null 2>&1
}
