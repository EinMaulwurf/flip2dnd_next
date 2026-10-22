#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export DOCKER_CONFIG="$PWD/.build-cache/docker"
mkdir -p "$DOCKER_CONFIG"
docker build -t flip2dnd-build -f docker/Dockerfile .
mkdir -p .build-cache/gradle .build-cache/android
if [ "$#" -eq 0 ]; then set -- :app:assembleDebug; fi
docker run --rm --user "$(id -u):$(id -g)" \
  -e HOME=/cache/android -e ANDROID_USER_HOME=/cache/android \
  -v "$PWD:/workspace" \
  -v "$PWD/.build-cache/gradle:/cache/gradle" \
  -v "$PWD/.build-cache/android:/cache/android" \
  flip2dnd-build --no-daemon --max-workers=2 "$@"
