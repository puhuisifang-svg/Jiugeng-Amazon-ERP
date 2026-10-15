#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repo_root=$(CDPATH= cd -- "$script_dir/../.." && pwd)
backend_dir="$repo_root/deliverables/backend/sprint-0/jiugeng-erp-backend"
mysql_image="mysql@sha256:64756cc92f707eb504496d774353990bcb0f6999ddf598b6ad188f2da66bd000"
work_dir=$(mktemp -d "${TMPDIR:-/tmp}/jiugeng-erp-backup-drill.XXXXXX")
chmod 700 "$work_dir"
run_id=$(basename "$work_dir" | tr -cd 'A-Za-z0-9')
container="jiugeng-erp-backup-$run_id"
backend_port=${ERP_BACKUP_VERIFY_PORT:-18081}
backend_pid=""

cleanup() {
  [ -z "$backend_pid" ] || kill "$backend_pid" 2>/dev/null || true
  [ -z "$backend_pid" ] || wait "$backend_pid" 2>/dev/null || true
  docker rm -f "$container" >/dev/null 2>&1 || true
  find "$work_dir" -type f -delete 2>/dev/null || true
  find "$work_dir" -depth -type d -empty -delete 2>/dev/null || true
}
trap cleanup EXIT HUP INT TERM

umask 077
openssl rand -hex 32 > "$work_dir/mysql_root_password"
openssl rand -hex 32 > "$work_dir/mysql_app_password"
{
  printf '%s\n' '[client]' 'user=erp_backup'
  printf 'password='; tr -d '\r\n' < "$work_dir/mysql_app_password"; printf '\n%s\n' 'host=127.0.0.1'
} > "$work_dir/client.cnf"
{
  printf '%s\n' '[client]' 'user=root'
  printf 'password='; tr -d '\r\n' < "$work_dir/mysql_root_password"; printf '\n%s\n' 'host=127.0.0.1'
} > "$work_dir/root.cnf"
mkdir "$work_dir/backups" "$work_dir/images"; chmod 700 "$work_dir/backups" "$work_dir/images"

docker run -d --name "$container" --pull never -p 127.0.0.1::3306 \
  -e MYSQL_DATABASE=erp_backup_verify -e MYSQL_USER=erp_backup \
  -e MYSQL_ROOT_PASSWORD_FILE=/run/secrets/mysql_root_password -e MYSQL_PASSWORD_FILE=/run/secrets/mysql_app_password \
  -v "$work_dir:/run/secrets:ro" \
  --health-cmd='mysqladmin ping --host=127.0.0.1 --silent' --health-interval=2s --health-timeout=2s \
  --health-retries=45 --health-start-period=10s "$mysql_image" >/dev/null

attempt=0
until [ "$(docker inspect --format '{{.State.Health.Status}}' "$container")" = healthy ]; do
  attempt=$((attempt+1)); [ "$attempt" -lt 60 ] || { echo "Temporary MySQL failed health check" >&2; exit 1; }; sleep 1
done
mysql_port=$(docker port "$container" 3306/tcp | sed -n '1s/.*://p')
(
  cd "$backend_dir"
  ERP_MYSQL_SECRET_DIR="$work_dir/" DATABASE_USERNAME=erp_backup \
    DATABASE_URL="jdbc:mysql://127.0.0.1:$mysql_port/erp_backup_verify?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true" \
    exec ./mvnw spring-boot:run -Dspring-boot.run.profiles=mysql -Dspring-boot.run.arguments="--server.port=$backend_port"
) > "$work_dir/backend.log" 2>&1 & backend_pid=$!
attempt=0
until curl -fsS "http://127.0.0.1:$backend_port/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; do
  attempt=$((attempt+1)); kill -0 "$backend_pid" 2>/dev/null || { sed -n '1,200p' "$work_dir/backend.log" >&2; exit 1; }
  [ "$attempt" -lt 90 ] || { sed -n '1,200p' "$work_dir/backend.log" >&2; exit 1; }; sleep 1
done
kill "$backend_pid"; wait "$backend_pid" 2>/dev/null || true; backend_pid=""

docker exec "$container" mysql --defaults-extra-file=/run/secrets/client.cnf erp_backup_verify --execute \
  "INSERT INTO schema_baseline(id,module_name,baseline_version) VALUES('00000000-0000-0000-0000-000000000020','erp-020-backup-drill','verified');"
printf '%s' 'dashboard-original-evidence' > "$work_dir/images/probe-original.png"
printf '%s' 'dashboard-thumbnail-evidence' > "$work_dir/images/probe-thumbnail.png"
docker exec "$container" mysql --defaults-extra-file=/run/secrets/client.cnf erp_backup_verify --execute \
  "INSERT INTO dashboard_image_asset(id,purpose,status,original_storage_key,thumbnail_storage_key,source_file_name,source_media_type,stored_media_type,byte_size,sha256,pixel_width,pixel_height,uploaded_by) VALUES('00000000-0000-0000-0000-000000000021','PENDING_PRODUCT','PENDING','probe-original.png','probe-thumbnail.png','probe.png','image/png','image/png',27,REPEAT('a',64),1,1,'backup-drill');"
before=$(docker exec "$container" mysql --defaults-extra-file=/run/secrets/client.cnf --batch --skip-column-names erp_backup_verify --execute \
  "SELECT CONCAT((SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1),'|',(SELECT COUNT(*) FROM schema_baseline),'|',(SELECT COUNT(*) FROM warehouse),'|',(SELECT COUNT(*) FROM dashboard_image_asset));")
file_before=$(sha256sum "$work_dir/images/probe-original.png" | awk '{print $1}')

docker run --rm --network "container:$container" -v "$repo_root:/repo:ro" -v "$work_dir:/work" "$mysql_image" sh -c \
  'cd /repo && ERP_MYSQL_CLIENT_CONFIG_FILE=/work/client.cnf ERP_MYSQL_DATABASE=erp_backup_verify ERP_DASHBOARD_WRITES_PAUSED=YES ERP_DASHBOARD_IMAGE_DIR=/work/images ERP_DASHBOARD_BACKUP_DIR=/work/backups /repo/scripts/ops/dashboard-backup.sh > /work/backup-path'
backup_name=$(basename "$(cat "$work_dir/backup-path")")
docker exec "$container" mysql --defaults-extra-file=/run/secrets/root.cnf --execute \
  "DROP DATABASE erp_backup_verify; CREATE DATABASE erp_backup_verify CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
find "$work_dir/images" -type f -delete
docker run --rm --network "container:$container" -v "$repo_root:/repo:ro" -v "$work_dir:/work" "$mysql_image" sh -c \
  "cd /repo && ERP_MYSQL_CLIENT_CONFIG_FILE=/work/client.cnf ERP_MYSQL_DATABASE=erp_backup_verify ERP_MYSQL_RESTORE_CONFIRM=erp_backup_verify ERP_DASHBOARD_WRITES_PAUSED=YES ERP_DASHBOARD_BACKUP_FILE=/work/backups/$backup_name ERP_DASHBOARD_IMAGE_DIR=/work/images ERP_DASHBOARD_IMAGE_RESTORE_CONFIRM=/work/images /repo/scripts/ops/dashboard-restore.sh"
after=$(docker exec "$container" mysql --defaults-extra-file=/run/secrets/client.cnf --batch --skip-column-names erp_backup_verify --execute \
  "SELECT CONCAT((SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1),'|',(SELECT COUNT(*) FROM schema_baseline),'|',(SELECT COUNT(*) FROM warehouse),'|',(SELECT COUNT(*) FROM dashboard_image_asset));")
[ "$before" = "$after" ]
[ -f "$work_dir/images/probe-original.png" ]; [ -f "$work_dir/images/probe-thumbnail.png" ]
[ "$(sha256sum "$work_dir/images/probe-original.png" | awk '{print $1}')" = "$file_before" ]
probe=$(docker exec "$container" mysql --defaults-extra-file=/run/secrets/client.cnf --batch --skip-column-names erp_backup_verify --execute \
  "SELECT COUNT(*) FROM schema_baseline WHERE module_name='erp-020-backup-drill' AND baseline_version='verified';")
[ "$probe" = 1 ]
echo "PASS: actual database and dashboard-image backup/restore preserved schema, counts and file hashes ($after)."
