#!/usr/bin/env bash
set -e

# GGDB_IMAGE is from https://github.com/GreengageDB/greengage/tree/main/ci
export GGDB_IMAGE=${GGDB_IMAGE:-greengagedb/ggdb6_ubuntu:6.29.1}
export IT_TAG=${IT_TAG:-it}

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
  bash compose.sh down # must be down if exit
} ; trap trap_exit EXIT

if [ "$BUILD_IMAGES" == "true" ]; then
  echo "------------"
  echo "Build images"
  echo "------------"
  bash build-images.sh
fi

echo "----------------"
echo "Run tests '$TESTS' without FDW"
echo "----------------"

unset USE_FDW
for test in $TESTS ; do
  GROUP=$test bash it.sh || was_failed=${was_failed:+$was_failed, }$test
done

bash compose.sh down # must be down before new next run

echo "----------------"
echo "Run tests '$TESTS' with FDW"
echo "----------------"

export USE_FDW=true
for test in $TESTS ; do
  GROUP=$test bash it.sh || was_failed=${was_failed:+$was_failed, }$test'(FDW)'
done

bash compose.sh down # must be down before new next run

echo "----------------"
echo "Run test 'ggdbssl' with FDW"
echo "----------------"

export USE_SSL=true
GROUP=$test bash it.sh || was_failed=${was_failed:+$was_failed, }${test}'(FDW, SSL)'

echo "-------------------------"
echo "TOTAL Check tests result status"
echo "-------------------------"
if [ -z "$was_failed" ]; then
  echo "----------------"
  echo "Grand TOTAL tests passed"
  echo "----------------"
  exit 0
else
  echo "----------------------------------------------"
  echo "Some tests from this groups was failed: $was_failed. Check logs and reports"
  echo "----------------------------------------------"
  exit 1
fi
