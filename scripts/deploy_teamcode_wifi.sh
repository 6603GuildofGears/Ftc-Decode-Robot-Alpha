#!/usr/bin/env bash
set -euo pipefail

# Wi‑Fi deploy helper for FTC TeamCode on REV Control Hub
#
# Usage examples:
#   scripts/deploy_teamcode_wifi.sh                # default 192.168.43.1:5555
#   scripts/deploy_teamcode_wifi.sh 192.168.43.1:5555
#   scripts/deploy_teamcode_wifi.sh --clean
#   scripts/deploy_teamcode_wifi.sh --restart
#   scripts/deploy_teamcode_wifi.sh --pkg com.qualcomm.ftcrobotcontroller --restart
#
# Notes:
# - Requires Android SDK platform-tools (adb) and working Gradle wrapper.
# - Installs the :TeamCode debug build onto the connected Control Hub.

DEFAULT_TARGET="192.168.43.1:5555"
APP_PKG="com.qualcomm.ftcrobotcontroller"  # Override with --pkg if needed
DO_CLEAN=0
DO_RESTART=0
TARGET="${DEFAULT_TARGET}"

log() { printf "[deploy] %s\n" "$*"; }
err() { printf "[deploy][error] %s\n" "$*" >&2; }

usage() {
  cat <<EOF
Usage: $0 [<ip:port>] [--clean] [--restart] [--pkg <package>]

Options:
  <ip:port>     Target ADB endpoint (default: ${DEFAULT_TARGET})
  --clean       Run './gradlew clean' before install
  --restart     Attempt to (re)launch the Robot Controller app after install
  --pkg <name>  Package to launch when --restart is set (default: ${APP_PKG})
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --clean)
      DO_CLEAN=1; shift ;;
    --restart)
      DO_RESTART=1; shift ;;
    --pkg)
      [[ $# -ge 2 ]] || { err "--pkg requires an argument"; exit 2; }
      APP_PKG="$2"; shift 2 ;;
    -h|--help)
      usage; exit 0 ;;
    *)
      # If it looks like host:port, treat as target
      if [[ "$1" == *:* ]]; then
        TARGET="$1"; shift
      else
        err "Unrecognized argument: $1"; usage; exit 2
      fi
      ;;
  esac
done

# Resolve repo root (assume script lives in scripts/ under the repo)
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# Locate adb
ADB=""
if [[ -x "/Users/ken/Library/Android/sdk/platform-tools/adb" ]]; then
  ADB="/Users/ken/Library/Android/sdk/platform-tools/adb"
elif command -v adb >/dev/null 2>&1; then
  ADB="$(command -v adb)"
elif [[ -n "${ANDROID_HOME:-}" && -x "${ANDROID_HOME}/platform-tools/adb" ]]; then
  ADB="${ANDROID_HOME}/platform-tools/adb"
elif [[ -n "${ANDROID_SDK_ROOT:-}" && -x "${ANDROID_SDK_ROOT}/platform-tools/adb" ]]; then
  ADB="${ANDROID_SDK_ROOT}/platform-tools/adb"
else
  err "adb not found. Install Android platform-tools or set ANDROID_HOME/ANDROID_SDK_ROOT."
  exit 1
fi

# Ensure gradlew is present
if [[ ! -x "${REPO_ROOT}/gradlew" ]]; then
  err "gradlew not found or not executable at ${REPO_ROOT}/gradlew"
  exit 1
fi

# Set JAVA_HOME if not already set
if [[ -z "${JAVA_HOME:-}" ]]; then
  # Try Homebrew OpenJDK 17 first (common on M1/M2 Macs)
  if [[ -d "/opt/homebrew/opt/openjdk@17" ]]; then
    export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
    log "JAVA_HOME set to Homebrew OpenJDK 17: ${JAVA_HOME}"
  # Try system java_home utility for JDK 17
  elif /usr/libexec/java_home -v 17 >/dev/null 2>&1; then
    export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
    log "JAVA_HOME set to JDK 17: ${JAVA_HOME}"
  # Fallback to JDK 11
  elif /usr/libexec/java_home -v 11 >/dev/null 2>&1; then
    export JAVA_HOME="$(/usr/libexec/java_home -v 11)"
    log "JAVA_HOME set to JDK 11: ${JAVA_HOME}"
  # Try any available JDK
  elif /usr/libexec/java_home >/dev/null 2>&1; then
    export JAVA_HOME="$(/usr/libexec/java_home)"
    log "JAVA_HOME set to default JDK: ${JAVA_HOME}"
  else
    err "No JDK found. Install OpenJDK 17 via: brew install openjdk@17"
    exit 1
  fi
fi

log "Connecting ADB to ${TARGET} ..."
"${ADB}" connect "${TARGET}" >/dev/null || true

if ! "${ADB}" devices | grep -q "${TARGET}"; then
  err "ADB does not list ${TARGET}. Ensure Control Hub is reachable over Wi‑Fi."
  "${ADB}" devices | sed 's/^/[adb] /'
  exit 1
fi
log "ADB connected to ${TARGET}."

if (( DO_CLEAN )); then
  log "Running clean ..."
  ./gradlew clean
fi

log "Installing :TeamCode:installDebug ... (this may take a bit)"
./gradlew :TeamCode:installDebug
log "Install complete."

if (( DO_RESTART )); then
  log "Attempting to (re)launch ${APP_PKG} via monkey ..."
  if "${ADB}" -s "${TARGET}" shell cmd package resolve-activity -c android.intent.category.LAUNCHER "${APP_PKG}" >/dev/null 2>&1; then
    "${ADB}" -s "${TARGET}" shell monkey -p "${APP_PKG}" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true
    log "Launch command sent."
  else
    err "Package ${APP_PKG} not found on device. Skipping restart."
  fi
fi

log "Done. You can now disconnect from this network if needed."
