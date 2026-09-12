#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repo_root=$(CDPATH= cd -- "$script_dir/../.." && pwd)
secret_dir=${ERP_LOCAL_SECRET_DIR:-"$repo_root/.local/secrets"}
mode=${1:-init}

case "$mode" in
  init|--rotate) ;;
  *) echo "usage: $0 [--rotate]" >&2; exit 2 ;;
esac

install -d -m 700 "$secret_dir"
umask 077

generate_secret() {
  target=$1
  bytes=$2
  openssl rand -hex "$bytes" > "$target"
  chmod 600 "$target"
}

ensure_secret() {
  target=$1
  bytes=$2
  if [ ! -s "$target" ]; then
    generate_secret "$target" "$bytes"
  fi
  chmod 600 "$target"
}

root_file="$secret_dir/mysql_root_password"
app_file="$secret_dir/mysql_app_password"
minio_user_file="$secret_dir/minio_root_user"
minio_password_file="$secret_dir/minio_root_password"

if [ "$mode" = "init" ]; then
  ensure_secret "$root_file" 32
  ensure_secret "$app_file" 32
  ensure_secret "$minio_user_file" 16
  ensure_secret "$minio_password_file" 32
  echo "local secret files ready; values were not displayed"
  exit 0
fi

for required in "$root_file" "$app_file" "$minio_user_file" "$minio_password_file"; do
  if [ ! -s "$required" ]; then
    echo "rotation requires an initialized secret set; run without --rotate first" >&2
    exit 1
  fi
done

work_dir=$(mktemp -d "${TMPDIR:-/tmp}/jiugeng-erp-secret-rotation.XXXXXX")
chmod 700 "$work_dir"
cleanup() {
  find "$work_dir" -type f -delete 2>/dev/null || true
  rmdir "$work_dir" 2>/dev/null || true
}
trap cleanup EXIT HUP INT TERM

generate_secret "$work_dir/mysql_root_password.next" 32
generate_secret "$work_dir/mysql_app_password.next" 32
generate_secret "$work_dir/minio_root_user.next" 16
generate_secret "$work_dir/minio_root_password.next" 32

{
  printf '%s\n' '[client]' 'user=root'
  printf 'password='
  tr -d '\r\n' < "$root_file"
  printf '\n'
} > "$work_dir/mysql-client.cnf"
chmod 600 "$work_dir/mysql-client.cnf"

app_user=${ERP_MYSQL_USER:-jiugeng_erp}
case "$app_user" in
  *[!A-Za-z0-9_]*) echo "ERP_MYSQL_USER contains unsupported characters" >&2; exit 1 ;;
esac

awk -v app_user="$app_user" '
  NR == FNR { gsub(/[\r\n]/, ""); root_password = $0; next }
  { gsub(/[\r\n]/, ""); app_password = $0 }
  END {
    printf "ALTER USER `root`@`localhost` IDENTIFIED BY \047%s\047;\n", root_password
    printf "ALTER USER `%s`@`%%` IDENTIFIED BY \047%s\047;\n", app_user, app_password
  }
' "$work_dir/mysql_root_password.next" "$work_dir/mysql_app_password.next" > "$work_dir/rotate.sql"
chmod 600 "$work_dir/rotate.sql"

cd "$repo_root"
docker compose up -d --pull never
attempt=0
until "$script_dir/compose-health.sh" >/dev/null 2>&1; do
  attempt=$((attempt + 1))
  if [ "$attempt" -ge 45 ]; then
    echo "services did not become healthy before rotation" >&2
    exit 1
  fi
  sleep 2
done

docker cp "$work_dir/mysql-client.cnf" jiugeng-erp-mysql:/tmp/mysql-client.cnf >/dev/null
docker cp "$work_dir/rotate.sql" jiugeng-erp-mysql:/tmp/rotate.sql >/dev/null
docker exec jiugeng-erp-mysql sh -c 'chmod 600 /tmp/mysql-client.cnf /tmp/rotate.sql; rc=0; mysql --defaults-extra-file=/tmp/mysql-client.cnf < /tmp/rotate.sql || rc=$?; rm -f /tmp/mysql-client.cnf /tmp/rotate.sql; exit "$rc"'

mv -f "$work_dir/mysql_root_password.next" "$root_file"
mv -f "$work_dir/mysql_app_password.next" "$app_file"
mv -f "$work_dir/minio_root_user.next" "$minio_user_file"
mv -f "$work_dir/minio_root_password.next" "$minio_password_file"
chmod 600 "$root_file" "$app_file" "$minio_user_file" "$minio_password_file"

docker compose up -d --force-recreate --pull never
echo "local secrets rotated and services recreated; values were not displayed"
