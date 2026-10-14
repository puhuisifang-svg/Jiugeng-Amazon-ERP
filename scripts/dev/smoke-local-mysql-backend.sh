#!/usr/bin/env sh
set -eu

backend_url=${ERP_BACKEND_URL:-http://127.0.0.1:8080}

curl -fsS "$backend_url/actuator/health" | grep -q '"status":"UP"'

for mock_path in /api/dashboard/summary /api/products /api/watch/events; do
  status=$(curl -sS -o /dev/null -w '%{http_code}' "$backend_url$mock_path")
  if [ "$status" != "404" ]; then
    echo "Expected mock-only endpoint $mock_path to return 404, got $status" >&2
    exit 1
  fi
done

echo "Persistent MySQL backend smoke checks passed; mock-only endpoints remain isolated."
