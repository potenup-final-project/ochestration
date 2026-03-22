#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BASE_URL="${1:-http://localhost:8080}"
LOG_FILE="${ROOT_DIR}/build/demo-bootrun.log"

mkdir -p "${ROOT_DIR}/build"

cd "$ROOT_DIR"

echo "[1/3] Starting backend..."
./gradlew bootRun >"$LOG_FILE" 2>&1 &
BOOT_PID=$!

cleanup() {
  if ps -p "$BOOT_PID" >/dev/null 2>&1; then
    kill "$BOOT_PID" >/dev/null 2>&1 || true
    wait "$BOOT_PID" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

echo "[2/3] Waiting for server to be ready..."
for _ in {1..60}; do
  if curl -sS "$BASE_URL/api/providers" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

if ! curl -sS "$BASE_URL/api/providers" >/dev/null 2>&1; then
  echo "Server did not become ready. Check log: $LOG_FILE"
  exit 1
fi

echo "[3/3] Running rehearsal script..."
"$ROOT_DIR/scripts/demo_rehearsal.sh" "$BASE_URL"

echo
echo "Done. Backend log: $LOG_FILE"
