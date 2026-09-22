#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKEND_DIR="$PROJECT_ROOT/deliverables/backend/sprint-0/jiugeng-erp-backend"
FRONTEND_DIR="$PROJECT_ROOT/frontend"
RUN_DIR="$PROJECT_ROOT/.local/run"

mkdir -p "$RUN_DIR"

if [[ -f "$RUN_DIR/backend.pid" ]] && kill -0 "$(<"$RUN_DIR/backend.pid")" 2>/dev/null; then
  echo "Backend is already running (PID $(<"$RUN_DIR/backend.pid"))."
  exit 1
fi

if [[ -f "$RUN_DIR/frontend.pid" ]] && kill -0 "$(<"$RUN_DIR/frontend.pid")" 2>/dev/null; then
  echo "Frontend is already running (PID $(<"$RUN_DIR/frontend.pid"))."
  exit 1
fi

if lsof -nP -iTCP:8080 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "Port 8080 is already in use."
  exit 1
fi

if lsof -nP -iTCP:5173 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "Port 5173 is already in use."
  exit 1
fi

(
  cd "$BACKEND_DIR"
  nohup ./mvnw spring-boot:run -Dspring-boot.run.profiles=mock \
    >"$RUN_DIR/backend.log" 2>&1 &
  echo $! >"$RUN_DIR/backend.pid"
)

(
  cd "$FRONTEND_DIR"
  nohup pnpm dev --host 127.0.0.1 --port 5173 \
    >"$RUN_DIR/frontend.log" 2>&1 &
  echo $! >"$RUN_DIR/frontend.pid"
)

for _ in {1..40}; do
  if curl -fs http://127.0.0.1:8080/actuator/health >/dev/null \
    && curl -fs http://127.0.0.1:5173/home >/dev/null; then
    echo "Local MVP is running: http://127.0.0.1:5173"
    exit 0
  fi
  sleep 0.5
done

echo "Local MVP did not become ready. Check logs under $RUN_DIR."
"$PROJECT_ROOT/scripts/dev/stop-local-mvp.sh" || true
exit 1
