#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repo_root=$(CDPATH= cd -- "$script_dir/../.." && pwd)
backend_dir="$repo_root/deliverables/backend/sprint-0/jiugeng-erp-backend"

secret_dir=${ERP_LOCAL_SECRET_DIR:-"$repo_root/.local/secrets"}
case "$secret_dir" in
  /*) ;;
  *) secret_dir="$repo_root/$secret_dir" ;;
esac

database_name=${ERP_MYSQL_DATABASE:-jiugeng_erp}
database_user=${ERP_MYSQL_USER:-jiugeng_erp}
database_port=${ERP_MYSQL_PORT:-13306}

case "$database_name" in
  *[!A-Za-z0-9_]*) echo "ERP_MYSQL_DATABASE contains unsupported characters" >&2; exit 2 ;;
esac
case "$database_user" in
  *[!A-Za-z0-9_]*) echo "ERP_MYSQL_USER contains unsupported characters" >&2; exit 2 ;;
esac
case "$database_port" in
  ''|*[!0-9]*) echo "ERP_MYSQL_PORT must be numeric" >&2; exit 2 ;;
esac

if [ ! -s "$secret_dir/mysql_app_password" ]; then
  echo "MySQL app secret is missing; run ./scripts/ops/init-local-secrets.sh through the approved Ops flow." >&2
  exit 1
fi

mysql_health=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' jiugeng-erp-mysql 2>/dev/null || true)
if [ "$mysql_health" != "healthy" ]; then
  echo "jiugeng-erp-mysql is not healthy; start infrastructure through the approved Ops flow." >&2
  exit 1
fi

export ERP_MYSQL_SECRET_DIR="$secret_dir/"
export DATABASE_USERNAME="$database_user"
export DATABASE_URL="jdbc:mysql://127.0.0.1:$database_port/$database_name?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true"

cd "$backend_dir"
exec ./mvnw spring-boot:run -Dspring-boot.run.profiles=mysql
