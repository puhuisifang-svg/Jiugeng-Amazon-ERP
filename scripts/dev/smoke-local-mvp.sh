#!/usr/bin/env bash
set -euo pipefail

curl -fsS http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"'
curl -fsS -H 'Authorization: Bearer mock-session-admin' \
  -H 'Origin: http://127.0.0.1:5173' \
  http://127.0.0.1:8080/api/dashboard/summary | grep -q '"source":"SYNTHETIC"'
curl -fsS -X POST -H 'Content-Type: application/json' \
  --data '{"role":"ADMIN"}' \
  http://127.0.0.1:8080/api/auth/login | grep -q '"role":"ADMIN"'
curl -fsS -H 'Authorization: Bearer mock-session-admin' \
  http://127.0.0.1:8080/api/products | grep -q '"productCode":"PROD-MOCK-001"'
curl -fsS -H 'Authorization: Bearer mock-session-product-material' \
  http://127.0.0.1:8080/api/materials | grep -q '"source":"SYNTHETIC"'
curl -fsS -H 'Authorization: Bearer mock-session-product-material' \
  http://127.0.0.1:8080/api/inventory/balances | grep -q '"warehouseCode":"WH-MOCK-001"'
curl -fsS -H 'Authorization: Bearer mock-session-selection' \
  http://127.0.0.1:8080/api/selection/candidates | grep -q '"source":"SYNTHETIC"'
curl -fsS -H 'Authorization: Bearer mock-session-selection' \
  http://127.0.0.1:8080/api/watch/events | grep -q '"source":"SYNTHETIC"'

curl -fsS -X POST -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer mock-session-product-material' \
  --data '{"productCode":"PROD-SMOKE-001","name":"烟测模拟产品","skuCodes":["SKU-SMOKE-A","SKU-SMOKE-B"]}' \
  http://127.0.0.1:8080/api/products | grep -q '"STORE-MOCK-US-05"'
curl -fsS -X POST -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer mock-session-product-material' \
  --data '{"materialCode":"MAT-SMOKE-001","name":"烟测模拟辅料","unit":"piece"}' \
  http://127.0.0.1:8080/api/materials | grep -q '"materialCode":"MAT-SMOKE-001"'

opening_payload='{"requestId":"SMOKE-OPENING-001","materialCode":"MAT-SMOKE-001","type":"OPENING","quantity":0,"reason":"零期初烟测"}'
curl -fsS -X POST -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer mock-session-product-material' \
  --data "$opening_payload" \
  http://127.0.0.1:8080/api/inventory/transactions | grep -q '"replayed":false'
curl -fsS -X POST -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer mock-session-product-material' \
  --data "$opening_payload" \
  http://127.0.0.1:8080/api/inventory/transactions | grep -q '"replayed":true'

negative_status="$(curl -sS -o /dev/null -w '%{http_code}' -X POST \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer mock-session-product-material' \
  --data '{"requestId":"SMOKE-OUTBOUND-001","materialCode":"MAT-SMOKE-001","type":"OUTBOUND","quantity":1,"reason":"负库存烟测"}' \
  http://127.0.0.1:8080/api/inventory/transactions)"
[[ "$negative_status" == "409" ]]

curl -fsS -X POST -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer mock-session-selection' \
  --data '{"status":"REVIEWING"}' \
  http://127.0.0.1:8080/api/selection/candidates/candidate-mock-002/transition | grep -q '"status":"REVIEWING"'
curl -fsS -X POST -H 'Authorization: Bearer mock-session-selection' \
  http://127.0.0.1:8080/api/watch/events/event-mock-001/confirm | grep -q '"replayed":false'
curl -fsS -X POST -H 'Authorization: Bearer mock-session-selection' \
  http://127.0.0.1:8080/api/watch/events/event-mock-001/confirm | grep -q '"replayed":true'

inventory_status="$(curl -sS -o /dev/null -w '%{http_code}' \
  -H 'Authorization: Bearer mock-session-selection' \
  http://127.0.0.1:8080/api/inventory/balances)"
[[ "$inventory_status" == "403" ]]

curl -fsS http://127.0.0.1:5173/home | grep -q '<div id="app"></div>'

echo "Local MVP smoke checks passed."
