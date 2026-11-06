#!/usr/bin/env bash
set -euo pipefail
umask 077
[[ $EUID == 0 ]] || exit 1
root=/srv/jiugeng
mkdir -p "$root/backups"
file="$root/backups/mysql-$(date -u +%Y%m%dT%H%M%SZ).sql.gz"
# pipefail ensures failed dumps cannot be mistaken for successful gzip files.
trap 'rm -f "$file.partial"' EXIT
mysqldump --single-transaction --quick --no-tablespaces --routines --triggers --set-gtid-purged=OFF jiugeng_erp | gzip > "$file.partial"
gzip -t "$file.partial"
mv "$file.partial" "$file"
sha256sum "$file" > "$file.sha256"
echo "Database backup completed"
