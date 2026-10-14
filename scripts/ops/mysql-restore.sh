#!/usr/bin/env sh
set -eu

: "${ERP_MYSQL_CLIENT_CONFIG_FILE:?set ERP_MYSQL_CLIENT_CONFIG_FILE to a protected MySQL defaults file}"
: "${ERP_MYSQL_DATABASE:?set ERP_MYSQL_DATABASE}"
: "${ERP_MYSQL_BACKUP_FILE:?set ERP_MYSQL_BACKUP_FILE}"
: "${ERP_MYSQL_RESTORE_CONFIRM:?set ERP_MYSQL_RESTORE_CONFIRM to the target database name}"

case "$ERP_MYSQL_DATABASE" in ''|*[!A-Za-z0-9_]*) echo "Invalid ERP_MYSQL_DATABASE" >&2; exit 2 ;; esac
[ "$ERP_MYSQL_RESTORE_CONFIRM" = "$ERP_MYSQL_DATABASE" ] || { echo "Restore confirmation does not match target database" >&2; exit 2; }
[ -f "$ERP_MYSQL_CLIENT_CONFIG_FILE" ] || { echo "MySQL client config does not exist" >&2; exit 2; }
[ -f "$ERP_MYSQL_BACKUP_FILE" ] || { echo "Backup file does not exist" >&2; exit 2; }
case "$ERP_MYSQL_BACKUP_FILE" in *.sql.gz) gzip -dc "$ERP_MYSQL_BACKUP_FILE" ;; *.sql) cat "$ERP_MYSQL_BACKUP_FILE" ;; *) echo "Backup must end in .sql or .sql.gz" >&2; exit 2 ;; esac |
  mysql --defaults-extra-file="$ERP_MYSQL_CLIENT_CONFIG_FILE" "$ERP_MYSQL_DATABASE"
echo "PASS: restore stream applied to explicitly confirmed target."
