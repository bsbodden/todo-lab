#!/usr/bin/env bash
# One command: build the wasm preview -> serve it -> run the device tests -> tear down.
# Usage:  ./preview-tests/run.sh        (resolves its own paths; override PORT=... if needed)
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"          # lab/todo-lab/preview-tests
LAB="$(cd "$HERE/.." && pwd)"                   # lab/todo-lab
REPO="$(cd "$LAB/../.." && pwd)"                # repo root
SERVE="$REPO/docs/kmp-corpus/serve-wasm.py"
STUDIO="$REPO/prototype/studio"
export JAVA_HOME="${JAVA_HOME:-$HOME/.sdkman/candidates/java/21.0.6-zulu}"
PORT="${PORT:-8082}"
STUDIO_PORT="${STUDIO_PORT:-8083}"
export BASE="http://127.0.0.1:$PORT"
export STUDIO_BASE="http://127.0.0.1:$STUDIO_PORT"

echo "==> [1/4] build the wasm preview (gradle)"
( cd "$LAB" && ./gradlew wasmJsBrowserDevelopmentExecutableDistribution --console=plain -q )

echo "==> [2/4] serve on :$PORT"
fuser -k "$PORT/tcp" 2>/dev/null || true; sleep 0.5
python3 "$SERVE" "$PORT" "$LAB/build/dist/wasmJs/developmentExecutable" >/tmp/todo-lab-preview-tests.log 2>&1 &
SERVER_PID=$!
trap 'kill "$SERVER_PID" 2>/dev/null || true' EXIT
sleep 2

# the 2nd real app (a port of LinuxCommandLibrary) — built + served so the catalog can load it live on :8084
LINUX_LAB="$REPO/lab/linux-lab"; LINUX_PORT="${LINUX_PORT:-8084}"
echo "==> [2b] build + serve linux-lab (2nd real app) on :$LINUX_PORT"
( cd "$LINUX_LAB" && ./gradlew wasmJsBrowserDevelopmentExecutableDistribution --console=plain -q )
fuser -k "$LINUX_PORT/tcp" 2>/dev/null || true; sleep 0.4
python3 "$SERVE" "$LINUX_PORT" "$LINUX_LAB/build/dist/wasmJs/developmentExecutable" >/tmp/linux-lab.log 2>&1 &
LINUX_PID=$!
trap 'kill "$SERVER_PID" "$LINUX_PID" 2>/dev/null || true' EXIT
sleep 2

# the 3rd real app (Cadence — a complex Spotify-style music app) — built + served on :8085
MUSIC_LAB="$REPO/lab/music-lab"; MUSIC_PORT="${MUSIC_PORT:-8085}"
echo "==> [2c] build + serve music-lab (complex app) on :$MUSIC_PORT"
( cd "$MUSIC_LAB" && ./gradlew wasmJsBrowserDevelopmentExecutableDistribution --console=plain -q )
fuser -k "$MUSIC_PORT/tcp" 2>/dev/null || true; sleep 0.4
python3 "$SERVE" "$MUSIC_PORT" "$MUSIC_LAB/build/dist/wasmJs/developmentExecutable" >/tmp/music-lab.log 2>&1 &
MUSIC_PID=$!
trap 'kill "$SERVER_PID" "$LINUX_PID" "$MUSIC_PID" 2>/dev/null || true' EXIT
sleep 2

echo "==> [3/4] ensure Playwright + Chromium"
cd "$HERE"
[ -d node_modules/playwright ] || npm install
node -e "require('playwright').chromium.executablePath()" >/dev/null 2>&1 \
  && node -e "const{existsSync}=require('fs');process.exit(existsSync(require('playwright').chromium.executablePath())?0:1)" \
  || npx playwright install chromium

echo "==> [4/6] run device tests (model-based UI + scenarios)"
npm test

echo "==> [5/6] serve the studio (JointJS minimap) on :$STUDIO_PORT"
( cd "$STUDIO" && [ -d node_modules/@joint/core ] || npm install )
fuser -k "$STUDIO_PORT/tcp" 2>/dev/null || true; sleep 0.4
python3 "$SERVE" "$STUDIO_PORT" "$STUDIO" >/tmp/kmpilot-studio.log 2>&1 &
STUDIO_PID=$!
trap 'kill "$SERVER_PID" "$LINUX_PID" "$MUSIC_PID" "$STUDIO_PID" 2>/dev/null || true' EXIT
sleep 1.5

echo "==> [6/7] run minimap test (ChartSpec → ELK → JointJS render + live highlight)"
npm run test:minimap

echo "==> [7/8] run interaction tests (click-to-navigate + state-machine drill-down)"
npm run test:interactions

echo "==> [8/8] run catalog tests (load every app from data/, in-progress stubs)"
npm run test:catalog

echo "==> ALL PREVIEW + MINIMAP + INTERACTION + CATALOG TESTS PASSED"
