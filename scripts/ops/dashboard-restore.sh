#!/usr/bin/env sh
set -eu

: "${ERP_DASHBOARD_WRITES_PAUSED:?set ERP_DASHBOARD_WRITES_PAUSED=YES after pausing backend writes}"
: "${ERP_DASHBOARD_BACKUP_FILE:?set ERP_DASHBOARD_BACKUP_FILE}"
: "${ERP_DASHBOARD_IMAGE_DIR:?set ERP_DASHBOARD_IMAGE_DIR}"
: "${ERP_DASHBOARD_IMAGE_RESTORE_CONFIRM:?set ERP_DASHBOARD_IMAGE_RESTORE_CONFIRM to the exact image directory}"
: "${ERP_CATALOG_IMAGE_DIR:?set ERP_CATALOG_IMAGE_DIR}"
: "${ERP_CATALOG_IMAGE_RESTORE_CONFIRM:?set ERP_CATALOG_IMAGE_RESTORE_CONFIRM to the exact catalog image directory}"
[ "$ERP_DASHBOARD_WRITES_PAUSED" = YES ] || { echo "Dashboard writes must be paused" >&2; exit 2; }
[ "$ERP_DASHBOARD_IMAGE_RESTORE_CONFIRM" = "$ERP_DASHBOARD_IMAGE_DIR" ] || { echo "Image restore confirmation mismatch" >&2; exit 2; }
[ "$ERP_CATALOG_IMAGE_RESTORE_CONFIRM" = "$ERP_CATALOG_IMAGE_DIR" ] || { echo "Catalog image restore confirmation mismatch" >&2; exit 2; }
[ -f "$ERP_DASHBOARD_BACKUP_FILE" ] || { echo "Dashboard backup does not exist" >&2; exit 2; }
case "$ERP_DASHBOARD_IMAGE_DIR" in ''|'/'|"$HOME") echo "Unsafe dashboard image restore target" >&2; exit 2 ;; esac
case "$ERP_CATALOG_IMAGE_DIR" in ''|'/'|"$HOME") echo "Unsafe catalog image restore target" >&2; exit 2 ;; esac
[ ! -L "$ERP_DASHBOARD_IMAGE_DIR" ] || { echo "Dashboard image target cannot be a symlink" >&2; exit 2; }
[ ! -L "$ERP_CATALOG_IMAGE_DIR" ] || { echo "Catalog image target cannot be a symlink" >&2; exit 2; }

umask 077
restore_dir=$(mktemp -d "${TMPDIR:-/tmp}/jiugeng-dashboard-restore.XXXXXX")
cleanup(){ find "$restore_dir" -type f -delete 2>/dev/null || true; find "$restore_dir" -depth -type d -empty -delete 2>/dev/null || true; }
trap cleanup EXIT HUP INT TERM
tar -xzf "$ERP_DASHBOARD_BACKUP_FILE" -C "$restore_dir"
manifest="$restore_dir/manifest.txt"; [ -f "$manifest" ] || { echo "Backup manifest missing" >&2; exit 2; }
value(){ sed -n "s/^$1=//p" "$manifest" | head -1; }
database_file=$(value database_file); database_sha=$(value database_sha256); images_sha=$(value images_sha256)
catalog_images_file=$(value catalog_images_file); catalog_images_sha=$(value catalog_images_sha256)
sha256(){ if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1"; else shasum -a 256 "$1"; fi; }
[ "$(sha256 "$restore_dir/$database_file" | awk '{print $1}')" = "$database_sha" ] || { echo "Database checksum mismatch" >&2; exit 1; }
[ "$(sha256 "$restore_dir/dashboard-images.tar.gz" | awk '{print $1}')" = "$images_sha" ] || { echo "Image checksum mismatch" >&2; exit 1; }
catalog_archive_present=0
if [ -n "$catalog_images_file" ]; then
  [ -f "$restore_dir/$catalog_images_file" ] || { echo "Catalog image archive missing" >&2; exit 2; }
  [ "$(sha256 "$restore_dir/$catalog_images_file" | awk '{print $1}')" = "$catalog_images_sha" ] || { echo "Catalog image checksum mismatch" >&2; exit 1; }
  catalog_archive_present=1
fi

scripts_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ERP_MYSQL_BACKUP_FILE="$restore_dir/$database_file" "$scripts_dir/mysql-restore.sh"
parent=$(dirname "$ERP_DASHBOARD_IMAGE_DIR"); mkdir -p "$parent"
new_dir="$parent/.dashboard-images-restore-$$"; mkdir "$new_dir"; tar -xzf "$restore_dir/dashboard-images.tar.gz" -C "$new_dir"
if [ -e "$ERP_DASHBOARD_IMAGE_DIR" ]; then
  previous="$parent/.dashboard-images-before-restore-$(date -u +%Y%m%dT%H%M%SZ)"
  mv "$ERP_DASHBOARD_IMAGE_DIR" "$previous"
  printf '%s\n' "Previous image directory retained at $previous"
fi
mv "$new_dir" "$ERP_DASHBOARD_IMAGE_DIR"; chmod 700 "$ERP_DASHBOARD_IMAGE_DIR"
catalog_parent=$(dirname "$ERP_CATALOG_IMAGE_DIR"); mkdir -p "$catalog_parent"
catalog_new_dir="$catalog_parent/.catalog-images-restore-$$"; mkdir "$catalog_new_dir"
if [ "$catalog_archive_present" = 1 ]; then tar -xzf "$restore_dir/$catalog_images_file" -C "$catalog_new_dir"; fi
if [ -e "$ERP_CATALOG_IMAGE_DIR" ]; then
  catalog_previous="$catalog_parent/.catalog-images-before-restore-$(date -u +%Y%m%dT%H%M%SZ)"
  mv "$ERP_CATALOG_IMAGE_DIR" "$catalog_previous"
  printf '%s\n' "Previous catalog image directory retained at $catalog_previous"
fi
mv "$catalog_new_dir" "$ERP_CATALOG_IMAGE_DIR"; chmod 700 "$ERP_CATALOG_IMAGE_DIR"
echo "PASS: database, dashboard images and catalog images restored as one verified unit."
