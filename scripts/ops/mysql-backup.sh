#!/usr/bin/env sh
set -eu

: "${ERP_MYSQL_CLIENT_CONFIG_FILE:?set ERP_MYSQL_CLIENT_CONFIG_FILE to a protected MySQL defaults file}"
: "${ERP_MYSQL_DATABASE:?set ERP_MYSQL_DATABASE}"
: "${ERP_MYSQL_BACKUP_DIR:?set ERP_MYSQL_BACKUP_DIR}"

case "$ERP_MYSQL_DATABASE" in ''|*[!A-Za-z0-9_]*) echo "Invalid ERP_MYSQL_DATABASE" >&2; exit 2 ;; esac
[ -f "$ERP_MYSQL_CLIENT_CONFIG_FILE" ] || { echo "MySQL client config does not exist" >&2; exit 2; }
mkdir -p "$ERP_MYSQL_BACKUP_DIR"
chmod 700 "$ERP_MYSQL_BACKUP_DIR"
backup_file="$ERP_MYSQL_BACKUP_DIR/${ERP_MYSQL_DATABASE}-$(date -u +%Y%m%dT%H%M%SZ).sql.gz"
umask 077
mysqldump --defaults-extra-file="$ERP_MYSQL_CLIENT_CONFIG_FILE" --single-transaction --quick --no-tablespaces \
  --routines --triggers --set-gtid-purged=OFF "$ERP_MYSQL_DATABASE" | gzip -c > "$backup_file"
chmod 600 "$backup_file"
[ -s "$backup_file" ] || { echo "Backup output is empty" >&2; exit 1; }
printf '%s\n' "$backup_file"
