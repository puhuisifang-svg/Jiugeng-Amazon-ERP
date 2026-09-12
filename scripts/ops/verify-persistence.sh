#!/usr/bin/env sh
set -eu

docker exec jiugeng-erp-mysql sh -c 'touch /var/lib/mysql/.ops-persistence-marker'
docker exec jiugeng-erp-redis redis-cli SET ops:persistence-marker verified >/dev/null
docker exec jiugeng-erp-minio sh -c 'touch /data/.ops-persistence-marker'

docker restart jiugeng-erp-mysql jiugeng-erp-redis jiugeng-erp-minio >/dev/null

attempt=0
while [ "$attempt" -lt 40 ]; do
  if ./scripts/ops/compose-health.sh >/dev/null 2>&1; then
    break
  fi
  attempt=$((attempt + 1))
  sleep 2
done

./scripts/ops/compose-health.sh
docker exec jiugeng-erp-mysql test -f /var/lib/mysql/.ops-persistence-marker
test "$(docker exec jiugeng-erp-redis redis-cli GET ops:persistence-marker)" = "verified"
docker exec jiugeng-erp-minio test -f /data/.ops-persistence-marker
echo "named-volume persistence verified after container restart"
