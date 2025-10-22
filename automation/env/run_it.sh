#!/usr/bin/env bash
set -eux

export TESTS='smoke gpdb jdbc'
export LOG_DIR='artifacts/docker_logs'

# shellcheck disable=SC2329
trap_exit() {
  if [ -n "$DEBUG" ]; then
    mkdir -p "$LOG_DIR"
    docker compose logs >> "$LOG_DIR/compose_before_exit.log"
    journalctl -exu docker >> "$LOG_DIR/docker.log"
    journalctl -exu containerd >> "$LOG_DIR/containerd.log"
  fi
  compose_down
} ; trap trap_exit EXIT

if [ "$BUILD_IMAGES" == "true" ]; then
  echo "------------"
  echo "Build images"
  echo "------------"
  bash build-images.sh
fi

echo "----------------"
echo "Start containers"
echo "----------------"

bash compose.sh up

for test in $TESTS ; do
  GROUP=$test bash it.sh
done

bash compose.sh down
bash compose.sh up

export USE_FDW=true
for test in $TESTS ; do
  GROUP=$test bash it.sh
done
bash compose.sh down
