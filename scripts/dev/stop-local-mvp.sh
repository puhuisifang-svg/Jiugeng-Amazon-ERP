#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RUN_DIR="$PROJECT_ROOT/.local/run"

stop_process() {
  local name="$1"
  local pid_file="$RUN_DIR/$name.pid"

  if [[ ! -f "$pid_file" ]]; then
    return
  fi

  local process_id
  process_id="$(<"$pid_file")"
  if kill -0 "$process_id" 2>/dev/null; then
    kill "$process_id"
    for _ in {1..20}; do
      if ! kill -0 "$process_id" 2>/dev/null; then
        break
      fi
      sleep 0.25
    done
  fi
  rm -f "$pid_file"
}

stop_process frontend
stop_process backend

echo "Local MVP processes stopped."
