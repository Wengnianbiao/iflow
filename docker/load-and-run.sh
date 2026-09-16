#!/bin/sh
# Load and run iFlow on openEuler x86_64 with Docker 20.10.x
# Usage:
#   ./load-and-run.sh /path/to/iflow-2.0.0-linux-amd64.tar.gz

set -eu

IMAGE_TAR="${1:-iflow-2.0.0-linux-amd64.tar.gz}"
IMAGE_NAME="${IMAGE_NAME:-iflow:2.0.0}"
CONTAINER_NAME="${CONTAINER_NAME:-iflow}"
MYSQL_HOST="${MYSQL_HOST:-132.147.160.230}"
MYSQL_PORT="${MYSQL_PORT:-2881}"
MYSQL_DB="${MYSQL_DB:-iflow}"
MYSQL_USER="${MYSQL_USER:-health_user}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-Helian@2022}"

if [ ! -f "$IMAGE_TAR" ]; then
  echo "image tar not found: $IMAGE_TAR" >&2
  exit 1
fi

docker load -i "$IMAGE_TAR"

if docker inspect "$CONTAINER_NAME" >/dev/null 2>&1; then
  docker rm -f "$CONTAINER_NAME"
fi

mkdir -p /opt/iflow/logs

# Host network: reach OceanBase/MySQL on 2881 and publish 8099 without Docker 20.10 DNAT issues.
docker run -d \
  --name "$CONTAINER_NAME" \
  --restart unless-stopped \
  --network host \
  -e PROFILES_ACTIVE=docker \
  -e SPRING_DATASOURCE_URL="jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DB}?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true" \
  -e SPRING_DATASOURCE_USERNAME="$MYSQL_USER" \
  -e SPRING_DATASOURCE_PASSWORD="$MYSQL_PASSWORD" \
  -v /opt/iflow/logs:/app/iflow \
  "$IMAGE_NAME"

echo "iFlow started. Open http://<euler-ip>:8099"
docker logs --tail 50 "$CONTAINER_NAME"
