#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repo_root=$(CDPATH= cd -- "$script_dir/../.." && pwd)
backend_dir="$repo_root/deliverables/backend/sprint-0/jiugeng-erp-backend"
mysql_image="mysql@sha256:64756cc92f707eb504496d774353990bcb0f6999ddf598b6ad188f2da66bd000"
backend_port=${ERP_VERIFY_BACKEND_PORT:-18080}

case "$backend_port" in
  ''|*[!0-9]*) echo "ERP_VERIFY_BACKEND_PORT must be numeric" >&2; exit 2 ;;
esac

if lsof -nP -iTCP:"$backend_port" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "Verification backend port $backend_port is already in use" >&2
  exit 1
fi

work_dir=$(mktemp -d "${TMPDIR:-/tmp}/jiugeng-erp-mysql-verify.XXXXXX")
chmod 700 "$work_dir"
run_id=$(basename "$work_dir" | tr -cd 'A-Za-z0-9')
mysql_container="jiugeng-erp-verify-mysql-$run_id"
mysql_volume="jiugeng-erp-verify-mysql-data-$run_id"
backend_pid=""

stop_backend() {
  if [ -n "$backend_pid" ] && kill -0 "$backend_pid" 2>/dev/null; then
    kill "$backend_pid"
    wait "$backend_pid" 2>/dev/null || true
  fi
  backend_pid=""
}

cleanup() {
  stop_backend
  docker rm -f "$mysql_container" >/dev/null 2>&1 || true
  docker volume rm "$mysql_volume" >/dev/null 2>&1 || true
  find "$work_dir" -type f -delete 2>/dev/null || true
  rmdir "$work_dir" 2>/dev/null || true
}
trap cleanup EXIT
trap 'exit 130' HUP INT TERM

umask 077
openssl rand -hex 32 > "$work_dir/mysql_root_password"
openssl rand -hex 32 > "$work_dir/mysql_app_password"
{
  printf '%s\n' '[client]' 'user=jiugeng_erp_verify'
  printf 'password='
  tr -d '\r\n' < "$work_dir/mysql_app_password"
  printf '\n%s\n' 'host=127.0.0.1'
} > "$work_dir/mysql-client.cnf"
chmod 600 "$work_dir"/*

docker image inspect "$mysql_image" >/dev/null
docker volume create "$mysql_volume" >/dev/null
docker run -d \
  --name "$mysql_container" \
  --pull never \
  -p 127.0.0.1::3306 \
  -e MYSQL_DATABASE=jiugeng_erp_verify \
  -e MYSQL_USER=jiugeng_erp_verify \
  -e MYSQL_ROOT_PASSWORD_FILE=/run/erp-secrets/mysql_root_password \
  -e MYSQL_PASSWORD_FILE=/run/erp-secrets/mysql_app_password \
  -v "$mysql_volume:/var/lib/mysql" \
  -v "$work_dir:/run/erp-secrets:ro" \
  --health-cmd='mysqladmin ping --host=127.0.0.1 --silent' \
  --health-interval=2s \
  --health-timeout=2s \
  --health-retries=45 \
  --health-start-period=10s \
  "$mysql_image" >/dev/null

wait_for_mysql() {
  attempts=0
  while [ "$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' "$mysql_container")" != "healthy" ]; do
    attempts=$((attempts + 1))
    if [ "$attempts" -ge 60 ]; then
      echo "Temporary MySQL did not become healthy" >&2
      exit 1
    fi
    sleep 1
  done
}

start_backend() {
  mysql_port=$(docker port "$mysql_container" 3306/tcp | sed -n '1s/.*://p')
  if [ -z "$mysql_port" ]; then
    echo "Could not resolve temporary MySQL port" >&2
    exit 1
  fi

  (
    cd "$backend_dir"
    ERP_MYSQL_SECRET_DIR="$work_dir/" \
      DATABASE_USERNAME=jiugeng_erp_verify \
      DATABASE_URL="jdbc:mysql://127.0.0.1:$mysql_port/jiugeng_erp_verify?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true" \
      exec ./mvnw spring-boot:run \
        -Dspring-boot.run.profiles=mysql \
        -Dspring-boot.run.arguments="--server.port=$backend_port"
  ) > "$work_dir/backend.log" 2>&1 &
  backend_pid=$!

  attempts=0
  until curl -fsS "http://127.0.0.1:$backend_port/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; do
    attempts=$((attempts + 1))
    if ! kill -0 "$backend_pid" 2>/dev/null; then
      echo "MySQL profile backend exited before becoming healthy" >&2
      sed -n '1,240p' "$work_dir/backend.log" >&2
      exit 1
    fi
    if [ "$attempts" -ge 60 ]; then
      echo "MySQL profile backend did not become healthy" >&2
      sed -n '1,240p' "$work_dir/backend.log" >&2
      exit 1
    fi
    sleep 1
  done
}

query_scalar() {
  sql=$1
  docker exec "$mysql_container" mysql \
    --defaults-extra-file=/run/erp-secrets/mysql-client.cnf \
    --batch --skip-column-names jiugeng_erp_verify \
    --execute "$sql"
}

wait_for_mysql
start_backend

baseline_count=$(query_scalar "SELECT COUNT(*) FROM schema_baseline WHERE module_name='modular-monolith' AND baseline_version='sprint-0'")
[ "$baseline_count" = "1" ]

query_scalar "INSERT INTO schema_baseline (id, module_name, baseline_version) VALUES ('00000000-0000-0000-0000-000000000007', 'erp-007-persistence-probe', 'restart-1')" >/dev/null
query_scalar "INSERT INTO product (id, product_code, product_code_normalized, name_zh, status, created_by, updated_by) VALUES ('00000000-0000-0000-0000-000000000010', 'ERP-010-PROBE', 'ERP-010-PROBE', '持久化探针', 'ACTIVE', 'system', 'system')" >/dev/null
query_scalar "INSERT INTO product_variant (id, product_id, sku_code, sku_code_normalized, variant_name, status, attribute_signature) VALUES ('00000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-000000000010', 'ERP-010-SKU', 'ERP-010-SKU', '探针 SKU', 'ACTIVE', '7bdb1b7ba001db3942d1996f891ac65fbc8f7d300bb344912bb105a256ea8b99')" >/dev/null
query_scalar "INSERT INTO variant_attribute (variant_id, attribute_code, attribute_code_normalized, attribute_value, display_order) VALUES ('00000000-0000-0000-0000-000000000011', 'color', 'COLOR', 'Black', 0)" >/dev/null
query_scalar "INSERT INTO auxiliary_material (id, material_code, material_code_normalized, name, material_type, specification, unit, status, created_by, updated_by) VALUES ('00000000-0000-0000-0000-000000000012', 'ERP-011-PROBE', 'ERP-011-PROBE', '辅料持久化探针', '包装', '标准', 'PCS', 'ACTIVE', 'system', 'system')" >/dev/null

stop_backend
docker restart "$mysql_container" >/dev/null
wait_for_mysql
start_backend

probe_count=$(query_scalar "SELECT COUNT(*) FROM schema_baseline WHERE module_name='erp-007-persistence-probe' AND baseline_version='restart-1'")
[ "$probe_count" = "1" ]
product_probe_count=$(query_scalar "SELECT COUNT(*) FROM product p JOIN product_variant v ON v.product_id=p.id JOIN variant_attribute a ON a.variant_id=v.id WHERE p.product_code='ERP-010-PROBE' AND v.sku_code='ERP-010-SKU' AND a.attribute_value='Black'")
[ "$product_probe_count" = "1" ]
material_probe_count=$(query_scalar "SELECT COUNT(*) FROM auxiliary_material WHERE material_code='ERP-011-PROBE' AND material_type='包装' AND status='ACTIVE'")
[ "$material_probe_count" = "1" ]

ERP_BACKEND_URL="http://127.0.0.1:$backend_port" "$repo_root/scripts/dev/smoke-local-mysql-backend.sh"

echo "PASS: MySQL profile migrated, survived restart, and kept mock endpoints isolated."
