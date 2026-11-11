#!/usr/bin/env bash
# FocusLens on Android: build, install, launch, logs and screenshots.
# Usage: run.sh devices|build|install|launch|logs|shot|all
set -euo pipefail

REPO="$(cd "$(dirname "$0")/../../../.." && pwd)"
APK="$REPO/app/build/outputs/apk/debug/app-debug.apk"
PKG="com.focuslens"
ACT="$PKG/.MainActivity"
SHOT_DIR="${SHOT_DIR:-${SCRATCH:-/tmp}/focuslens-android}"

need() { command -v "$1" >/dev/null 2>&1 || { echo "Missing: $1 (install it or add it to PATH)"; exit 1; }; }

adb_cmd() {
  if [[ -n "${ADB_SERIAL:-}" ]]; then adb -s "$ADB_SERIAL" "$@"; else adb "$@"; fi
}

check_device() {
  need adb
  local n
  n=$(adb devices | awk 'NR>1 && $2=="device"' | wc -l)
  if [[ "$n" -eq 0 ]]; then echo "No device. Start an emulator in Android Studio or plug in a phone with USB debugging."; exit 1; fi
  if [[ "$n" -gt 1 && -z "${ADB_SERIAL:-}" ]]; then echo "More than one device. Set ADB_SERIAL to one of:"; adb devices; exit 1; fi
}

cmd_devices() { need adb; adb devices; }

cmd_build() {
  cd "$REPO"
  if [[ ! -f local.properties ]]; then echo "No local.properties. Open the project in Android Studio once."; exit 1; fi
  if command -v gradle >/dev/null 2>&1; then gradle :app:assembleDebug --no-daemon
  else ./gradlew :app:assembleDebug --no-daemon; fi
  echo "Built: $APK"
}

cmd_install() {
  check_device
  [[ -f "$APK" ]] || { echo "No APK yet. Run: run.sh build"; exit 1; }
  adb_cmd install -r "$APK"
}

cmd_launch() {
  check_device
  adb_cmd shell am start -n "$ACT"
}

cmd_logs() {
  check_device
  adb_cmd logcat -c
  adb_cmd logcat -v time -s FocusLens:D
}

cmd_shot() {
  check_device
  mkdir -p "$SHOT_DIR"
  local f="$SHOT_DIR/shot_$(date +%Y%m%d_%H%M%S).png"
  adb_cmd exec-out screencap -p > "$f"
  echo "Saved $f"
}

case "${1:-}" in
  devices) cmd_devices ;;
  build)   cmd_build ;;
  install) cmd_install ;;
  launch)  cmd_launch ;;
  logs)    cmd_logs ;;
  shot)    cmd_shot ;;
  all)     cmd_build; cmd_install; cmd_launch; sleep 3; cmd_shot ;;
  *) echo "Usage: $0 devices|build|install|launch|logs|shot|all"; exit 1 ;;
esac
