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

# Publish container 8099 on every host address, including 132.147.160.231:8099.
# extra_hosts: JDBC 建连会反查容器主机名/本机 IP；容器 /etc/hosts 与宿主机隔离，不配会固定卡约 10s。
docker run -d \
  --name "$CONTAINER_NAME" \
  --restart unless-stopped \
  -p 8099:8099 \
  --hostname localhost.localdomain \
  --add-host=iflow:127.0.0.1 \
  --add-host=localhost.localdomain:127.0.0.1 \
  --add-host=localhost.localdomain:132.147.160.231 \
  --add-host=ob:132.147.160.230 \
  -e PROFILES_ACTIVE=docker \
  -e SPRING_DATASOURCE_URL="jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DB}?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true" \
  -e SPRING_DATASOURCE_USERNAME="$MYSQL_USER" \
  -e SPRING_DATASOURCE_PASSWORD="$MYSQL_PASSWORD" \
  -v /opt/iflow/logs:/app/iflow \
  "$IMAGE_NAME"

echo "iFlow started. Open http://<euler-ip>:8099"
docker logs --tail 50 "$CONTAINER_NAME"
