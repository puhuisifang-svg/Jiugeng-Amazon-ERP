#!/usr/bin/env bash
# Root-owned, sudo permitted only for the dedicated deployment account.
set -Eeuo pipefail
umask 022
[[ $EUID == 0 && $# == 2 && $1 =~ ^[0-9a-f]{40}$ && $2 =~ ^[0-9a-f]{64}$ ]] || exit 2
sha=$1 digest=$2 root=/srv/jiugeng
exec 9>/run/lock/jiugeng-deploy.lock
flock -n 9 || { echo 'Another deployment is running' >&2; exit 1; }
# This marker is deliberately absent until initial migration has been accepted.
[[ -f /etc/jiugeng/deploy-enabled ]] || { echo 'Deployment gate is not enabled' >&2; exit 1; }
[[ $(df -Pk "$root" | awk 'NR==2 {print $4}') -gt 2097152 ]] || { echo 'Less than 2 GiB free'; exit 1; }
tmp=$(mktemp -d "$root/.release.XXXXXX")
trap 'rm -rf -- "$tmp"' EXIT
# Limit stream input and unpacked size independently.
head -c 536870913 > "$tmp/release.tar.gz"
[[ $(stat -c %s "$tmp/release.tar.gz") -le 536870912 ]] || exit 1
printf '%s  %s\n' "$digest" "$tmp/release.tar.gz" | sha256sum -c -
python3 /usr/local/lib/jiugeng/release.py unpack "$tmp/release.tar.gz" "$tmp/unpacked" "$sha"
[[ ! -e "$root/releases/$sha" ]] || { echo 'Version already exists; use a new build or explicit recovery'; exit 1; }
old=$(readlink -f "$root/current" || true)
activated=false
unexpected_failure() {
  trap - ERR
  if [[ $activated == false && -n "$old" ]]; then
    systemctl start jiugeng || true
    echo 'Deployment preparation failed; previous application restart requested' >&2
  else
    systemctl stop jiugeng || true
    echo 'Unexpected deployment failure; service stopped for inspection; database retained' >&2
  fi
  exit 1
}
trap unexpected_failure ERR
# Stop the sole writer before backup. Jobs and migrations must not race backup.
systemctl stop jiugeng
if ! /usr/local/lib/jiugeng/backup.sh; then
  [[ -z "$old" ]] || systemctl start jiugeng
  exit 1
fi
schema() { mysql --batch --skip-column-names jiugeng_erp -e 'SELECT installed_rank,version,checksum,success FROM flyway_schema_history ORDER BY installed_rank' | sha256sum | cut -d' ' -f1; }
before=$(schema)
mv "$tmp/unpacked" "$root/releases/$sha"
ln -s "$root/releases/$sha" "$root/current.new"
mv -Tf "$root/current.new" "$root/current"
activated=true
healthy=false
if systemctl start jiugeng; then
 for ((i=0;i<90;i++)); do
  if curl -fsS --max-time 3 http://127.0.0.1:8080/actuator/health | python3 -c 'import json,sys;sys.exit(json.load(sys.stdin).get("status")!="UP")' 2>/dev/null; then healthy=true; break; fi
  sleep 2
 done
fi
if [[ $healthy == true ]]; then
 served=$(curl -fsS --max-time 5 http://127.0.0.1:4173/version.json | python3 -c 'import json,sys;print(json.load(sys.stdin)["revision"])') || healthy=false
 [[ ${served:-} == "$sha" ]] || healthy=false
fi
if [[ $healthy != true ]]; then
 systemctl stop jiugeng
 after=$(schema) || after=unknown
 if [[ -n "$old" && "$before" == "$after" ]]; then
  ln -s "$old" "$root/current.rollback"
  mv -Tf "$root/current.rollback" "$root/current"
  systemctl start jiugeng
  restored=false
  for ((i=0;i<90;i++)); do
    if curl -fsS --max-time 3 http://127.0.0.1:8080/actuator/health | python3 -c 'import json,sys;sys.exit(json.load(sys.stdin).get("status")!="UP")' 2>/dev/null; then restored=true; break; fi
    sleep 2
  done
  echo "Deployment failed; previous application restored; health verified: $restored" >&2
 else
  echo 'Deployment failed after database change; service stopped. Manual forward fix required; database was NOT restored.' >&2
 fi
 exit 1
fi
printf '%s %s\n' "$(date -u +%FT%TZ)" "$sha" >> "$root/deployments.log"
echo "Deployment verified: $sha"
