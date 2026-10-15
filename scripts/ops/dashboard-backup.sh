#!/usr/bin/env sh
set -eu

: "${ERP_DASHBOARD_WRITES_PAUSED:?set ERP_DASHBOARD_WRITES_PAUSED=YES after pausing backend writes}"
: "${ERP_DASHBOARD_IMAGE_DIR:?set ERP_DASHBOARD_IMAGE_DIR}"
: "${ERP_DASHBOARD_BACKUP_DIR:?set ERP_DASHBOARD_BACKUP_DIR}"
[ "$ERP_DASHBOARD_WRITES_PAUSED" = YES ] || { echo "Dashboard writes must be paused" >&2; exit 2; }
[ -d "$ERP_DASHBOARD_IMAGE_DIR" ] || { echo "Dashboard image directory does not exist" >&2; exit 2; }
[ ! -L "$ERP_DASHBOARD_IMAGE_DIR" ] || { echo "Dashboard image directory cannot be a symlink" >&2; exit 2; }

mkdir -p "$ERP_DASHBOARD_BACKUP_DIR"; chmod 700 "$ERP_DASHBOARD_BACKUP_DIR"; umask 077
stamp=$(date -u +%Y%m%dT%H%M%SZ)
bundle_dir="$ERP_DASHBOARD_BACKUP_DIR/.dashboard-$stamp-$$"
archive="$ERP_DASHBOARD_BACKUP_DIR/dashboard-$stamp.tar.gz"
mkdir "$bundle_dir"
cleanup(){ find "$bundle_dir" -type f -delete 2>/dev/null || true; find "$bundle_dir" -depth -type d -empty -delete 2>/dev/null || true; }
trap cleanup EXIT HUP INT TERM

scripts_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ERP_MYSQL_BACKUP_DIR="$bundle_dir" "$scripts_dir/mysql-backup.sh" > "$bundle_dir/database-path"
database_file=$(basename "$(cat "$bundle_dir/database-path")")
tar -C "$ERP_DASHBOARD_IMAGE_DIR" -czf "$bundle_dir/dashboard-images.tar.gz" .

schema_version=$(mysql --defaults-extra-file="$ERP_MYSQL_CLIENT_CONFIG_FILE" --batch --skip-column-names \
  "$ERP_MYSQL_DATABASE" --execute "SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1;")
image_counts=$(mysql --defaults-extra-file="$ERP_MYSQL_CLIENT_CONFIG_FILE" --batch --skip-column-names \
  "$ERP_MYSQL_DATABASE" --execute "SELECT CONCAT(COUNT(*),'|',SUM(status='ACTIVE'),'|',SUM(status='PENDING')) FROM dashboard_image_asset;")
sha256(){ if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1"; else shasum -a 256 "$1"; fi; }
database_sha=$(sha256 "$bundle_dir/$database_file" | awk '{print $1}')
images_sha=$(sha256 "$bundle_dir/dashboard-images.tar.gz" | awk '{print $1}')
{
  printf '%s\n' "created_at=$stamp" "database_file=$database_file" "database_sha256=$database_sha"
  printf '%s\n' "images_file=dashboard-images.tar.gz" "images_sha256=$images_sha" "flyway_version=$schema_version" "image_counts=$image_counts"
} > "$bundle_dir/manifest.txt"
tar -C "$bundle_dir" -czf "$archive" "$database_file" dashboard-images.tar.gz manifest.txt
chmod 600 "$archive"
[ -s "$archive" ] || { echo "Dashboard backup is empty" >&2; exit 1; }
printf '%s\n' "$archive"
