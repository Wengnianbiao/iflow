#!/bin/sh
# One-shot: package iflow.jar, build linux/amd64 image, export Docker 20.10 tar.gz
# Usage (from repo root or this directory):
#   ./docker/build-image.sh

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ROOT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
IMAGE_NAME="${IMAGE_NAME:-iflow:2.0.0}"
OUT_DIR="${OUT_DIR:-$ROOT_DIR/dist}"
OUT_GZ="$OUT_DIR/iflow-2.0.0-linux-amd64.tar.gz"

cd "$ROOT_DIR"

echo "==> iflow-ui production build"
if ! command -v pnpm >/dev/null 2>&1; then
  echo "pnpm not found; install pnpm before packaging the console UI" >&2
  exit 1
fi
(
  cd "$ROOT_DIR/iflow-ui"
  if [ ! -d node_modules ]; then
    pnpm install
  fi
  pnpm build
)

echo "==> maven package"
mvn -pl iflow-web -am clean package -Dmaven.test.skip=true

if [ ! -f "$ROOT_DIR/iflow-web/target/iflow.jar" ]; then
  echo "missing iflow-web/target/iflow.jar" >&2
  exit 1
fi

echo "==> docker build linux/amd64 $IMAGE_NAME"
# Docker Hub is often unreachable in CN; try local tag first, then mirrors.
BASE_CANDIDATES="${BASE_IMAGE:-}
eclipse-temurin:21-jdk-jammy
docker.m.daocloud.io/library/eclipse-temurin:21-jdk-jammy
docker.1ms.run/library/eclipse-temurin:21-jdk-jammy"

build_image() {
  base=$1
  echo "==> using base $base"
  docker buildx build \
    --platform linux/amd64 \
    --pull=false \
    --provenance=false \
    --sbom=false \
    --build-arg BASE_IMAGE="$base" \
    -t "$IMAGE_NAME" \
    -f docker/Dockerfile \
    --load \
    .
}

ok=0
for base in $BASE_CANDIDATES; do
  [ -n "$base" ] || continue
  if docker image inspect "$base" >/dev/null 2>&1; then
    if build_image "$base"; then
      ok=1
      break
    fi
  fi
done

if [ "$ok" -eq 0 ]; then
  for base in $BASE_CANDIDATES; do
    [ -n "$base" ] || continue
    echo "==> pulling $base"
    if docker pull --platform linux/amd64 "$base" && build_image "$base"; then
      ok=1
      break
    fi
  done
fi

if [ "$ok" -eq 0 ]; then
  echo "failed to build image: cannot pull eclipse-temurin:21-jdk-jammy" >&2
  echo "set BASE_IMAGE to a reachable Java 21 JDK, or configure a Docker Hub mirror" >&2
  exit 1
fi

echo "==> export $OUT_GZ"
mkdir -p "$OUT_DIR"
WORKDIR=$(mktemp -d)
trap 'rm -rf "$WORKDIR"' EXIT

export COPYFILE_DISABLE=1
docker save "$IMAGE_NAME" | tar -x -C "$WORKDIR"
rm -f "$WORKDIR/index.json" "$WORKDIR/oci-layout"

python3 - "$WORKDIR" <<'PY'
import json, os, sys
root = sys.argv[1]
manifest_path = os.path.join(root, "manifest.json")
with open(manifest_path) as f:
    manifest = json.load(f)
keep = set()
for item in manifest:
    keep.add(item["Config"])
    keep.update(item["Layers"])
blobs = os.path.join(root, "blobs", "sha256")
if os.path.isdir(blobs):
    for name in os.listdir(blobs):
        rel = f"blobs/sha256/{name}"
        if rel not in keep:
            os.remove(os.path.join(blobs, name))
PY

rm -f "$OUT_GZ"
COPYFILE_DISABLE=1 tar --no-xattrs -C "$WORKDIR" -cf - manifest.json repositories blobs 2>/dev/null \
  | gzip -1 > "$OUT_GZ" \
  || COPYFILE_DISABLE=1 tar -C "$WORKDIR" -cf - manifest.json repositories blobs \
  | gzip -1 > "$OUT_GZ"

cp "$ROOT_DIR/docker/load-and-run.sh" "$OUT_DIR/load-and-run.sh"
chmod +x "$OUT_DIR/load-and-run.sh"
ls -lh "$OUT_GZ" "$OUT_DIR/load-and-run.sh"
echo "==> done. copy dist/* to Euler /opt/iflow, then: ./load-and-run.sh /opt/iflow/iflow-2.0.0-linux-amd64.tar.gz"
