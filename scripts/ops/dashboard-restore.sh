#!/usr/bin/env sh
set -eu

: "${ERP_DASHBOARD_WRITES_PAUSED:?set ERP_DASHBOARD_WRITES_PAUSED=YES after pausing backend writes}"
: "${ERP_DASHBOARD_BACKUP_FILE:?set ERP_DASHBOARD_BACKUP_FILE}"
: "${ERP_DASHBOARD_IMAGE_DIR:?set ERP_DASHBOARD_IMAGE_DIR}"
: "${ERP_DASHBOARD_IMAGE_RESTORE_CONFIRM:?set ERP_DASHBOARD_IMAGE_RESTORE_CONFIRM to the exact image directory}"
[ "$ERP_DASHBOARD_WRITES_PAUSED" = YES ] || { echo "Dashboard writes must be paused" >&2; exit 2; }
[ "$ERP_DASHBOARD_IMAGE_RESTORE_CONFIRM" = "$ERP_DASHBOARD_IMAGE_DIR" ] || { echo "Image restore confirmation mismatch" >&2; exit 2; }
[ -f "$ERP_DASHBOARD_BACKUP_FILE" ] || { echo "Dashboard backup does not exist" >&2; exit 2; }
case "$ERP_DASHBOARD_IMAGE_DIR" in ''|'/'|"$HOME") echo "Unsafe dashboard image restore target" >&2; exit 2 ;; esac
[ ! -L "$ERP_DASHBOARD_IMAGE_DIR" ] || { echo "Dashboard image target cannot be a symlink" >&2; exit 2; }

umask 077
restore_dir=$(mktemp -d "${TMPDIR:-/tmp}/jiugeng-dashboard-restore.XXXXXX")
cleanup(){ find "$restore_dir" -type f -delete 2>/dev/null || true; find "$restore_dir" -depth -type d -empty -delete 2>/dev/null || true; }
trap cleanup EXIT HUP INT TERM
tar -xzf "$ERP_DASHBOARD_BACKUP_FILE" -C "$restore_dir"
manifest="$restore_dir/manifest.txt"; [ -f "$manifest" ] || { echo "Backup manifest missing" >&2; exit 2; }
value(){ sed -n "s/^$1=//p" "$manifest" | head -1; }
database_file=$(value database_file); database_sha=$(value database_sha256); images_sha=$(value images_sha256)
sha256(){ if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1"; else shasum -a 256 "$1"; fi; }
[ "$(sha256 "$restore_dir/$database_file" | awk '{print $1}')" = "$database_sha" ] || { echo "Database checksum mismatch" >&2; exit 1; }
[ "$(sha256 "$restore_dir/dashboard-images.tar.gz" | awk '{print $1}')" = "$images_sha" ] || { echo "Image checksum mismatch" >&2; exit 1; }

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
echo "PASS: database and dashboard images restored as one verified unit."
