#!/usr/bin/env sh
set -eu

services="jiugeng-erp-mysql jiugeng-erp-redis jiugeng-erp-minio"

for service in $services; do
  status=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' "$service")
  if [ "$status" != "healthy" ]; then
    echo "$service health=$status" >&2
    exit 1
  fi
  echo "$service health=healthy"
done
