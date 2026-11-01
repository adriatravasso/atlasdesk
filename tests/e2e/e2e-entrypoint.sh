#!/bin/sh
set -eu

cd /e2e
mode="${1:-headless}"
shift || true
lock_hash="$(sha256sum package-lock.json | cut -d ' ' -f 1)"
if [ ! -x node_modules/.bin/cypress ] || [ ! -f node_modules/.atlasdesk-lock-hash ] \
    || [ "$(cat node_modules/.atlasdesk-lock-hash)" != "$lock_hash" ]; then
  npm ci --prefer-offline
  printf '%s\n' "$lock_hash" >node_modules/.atlasdesk-lock-hash
fi
mkdir -p artifacts/reports artifacts/screenshots artifacts/videos

case "$mode" in
  headless)
    exec npx cypress run --browser chrome "$@"
    ;;
  visual)
    Xvfb :99 -screen 0 1440x1000x24 -ac +extension GLX +render -noreset &
    export DISPLAY=:99
    x11vnc -display :99 -forever -shared -localhost -nopw -rfbport 5900 >/tmp/x11vnc.log 2>&1 &
    websockify --web /usr/share/novnc 0.0.0.0:6080 127.0.0.1:5900 >/tmp/novnc.log 2>&1 &
    exec npx cypress run --headed --browser chrome "$@"
    ;;
  interactive)
    Xvfb :99 -screen 0 1440x1000x24 -ac +extension GLX +render -noreset &
    export DISPLAY=:99
    x11vnc -display :99 -forever -shared -localhost -nopw -rfbport 5900 >/tmp/x11vnc.log 2>&1 &
    websockify --web /usr/share/novnc 0.0.0.0:6080 127.0.0.1:5900 >/tmp/novnc.log 2>&1 &
    exec npx cypress open --browser chrome "$@"
    ;;
  *)
    echo "Usage: e2e-entrypoint.sh [headless|visual|interactive]" >&2
    exit 2
    ;;
esac
