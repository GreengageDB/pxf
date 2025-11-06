#!/bin/bash
# --- Presets ---
export SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
CONFIG=$1 ; export CONFIG=${CONFIG:-$SCRIPT_DIR/../../.github/workflows/greengage-ci.yml}

# Check YQ Utility
yq_version=$(yq --version 2>/dev/null)
if [ -z "$yq_version" ] ; then
  echo -n "Utility YQ not found but required. Try to install... "
  install_log=$(mktemp)
  sudo wget https://github.com/mikefarah/yq/releases/latest/download/yq_linux_amd64 -O /usr/local/bin/yq &> $install_log
  sudo chmod +x /usr/local/bin/yq &>> $install_log
  yq_version=$(yq --version 2>/dev/null)
  [ -z "$yq_version" ] && { echo "failed. Process terminated. See log: 'cat $install_log'" ; exit 1; } \
               || { echo -ne "completed. Installed $yq_version\n" ; rm -f $install_log; }
else
  echo "Utility YQ found: $yq_version"
fi

# Check config file
[ -r "$CONFIG" ] || { echo "Config file '$CONFIG' not readable. Process terminated"; exit 1; }

# --- Configure ---
export KEY_ENV='.jobs.integration.env'
export KEY_TESTS='.jobs.integration.strategy.matrix.include'

export GGDB_IMAGE=$(yq "${KEY_ENV}.GGDB_IMAGE // \"ghcr.io/greengagedb/greengage/ggdb6_ubuntu:latest\"" "$CONFIG")
export IT_IMAGE=$(yq "${KEY_ENV}.IT_IMAGE // \"greengagedb/ggdb6_pxf_automation\"" "$CONFIG")
export IT_TAG=$(yq "${KEY_ENV}.IT_TAG // \"it\"" "$CONFIG")

export DEBUG_DIR=$(yq "${KEY_ENV}.DEBUG_DIR // \"artifacts/docker_logs\"" "$CONFIG")
export DEBUG=$(yq "${KEY_ENV}.DEBUG // \"\"" "$CONFIG")

# shellcheck disable=SC2329
trap_exit() {
  if [ -n "$DEBUG" ]; then
    mkdir -p "$DEBUG_DIR"
    docker compose logs >> "$DEBUG_DIR/compose_before_exit.log"
    journalctl -exu docker >> "$DEBUG_DIR/docker.log"
    journalctl -exu containerd >> "$DEBUG_DIR/containerd.log"
  fi
  bash $SCRIPT_DIR/compose.sh down # must be down if exit
} ; trap trap_exit EXIT

# --- Begin ---
set -e
if [ "$BUILD_IMAGES" == "true" ]; then
  echo "------------"
  echo "Force (re)build image $IT_IMAGE:$IT_TAG"
  echo "------------"
  bash $SCRIPT_DIR/build-images.sh
fi

if ! docker image inspect $IT_IMAGE:$IT_TAG &>/dev/null ; then
  echo "------------"
  echo "Integreation tests image $IT_IMAGE:$IT_TAG not found locally. Building"
  echo "------------"
  bash $SCRIPT_DIR/build-images.sh
fi

tests_num=$(yq  "$KEY_TESTS | length" "$CONFIG")
echo "----------------"
echo "Tests found: $tests_num"
echo "----------------"

unset was_failed
for n in $(seq 0 $(($tests_num-1))) ; do
  export GROUP=$(yq "$KEY_TESTS[$n].test" "$CONFIG")
  export USE_FDW=$(yq "$KEY_TESTS[$n].fdw // \"\"" "$CONFIG")
  export USE_SSL=$(yq "$KEY_TESTS[$n].ssl // \"\"" "$CONFIG")
  export PROFILE=$(yq "$KEY_TESTS[$n].profile // \"\"" "$CONFIG")
  echo "---------------------------------------------------------------------------------"
  echo "Run test #$(($n+1)) of $tests_num with: GROUP='$GROUP', FDW='${USE_FDW:-false}', SSL='${USE_SSL:-false}', PROFILE='${PROFILE:-$GROUP}'"
  echo "---------------------------------------------------------------------------------"
  pushd $SCRIPT_DIR
  if ! bash $SCRIPT_DIR/it.sh ; then  # Collect failed tests
    unset opts
    [ -n "$USE_FDW" ] && opts=${opts:+$opts,}FDW || true
    [ -n "$USE_SSL" ] && opts=${opts:+$opts,}SSL || true
    was_failed=${was_failed:+$was_failed, }$GROUP${opts:+"($opts)"}
  fi
  popd
done

echo $was_failed
if [ -z "$was_failed" ]; then
  echo "----------------------------"
  echo "Grand TOTAL $tests_num test(s) passed"
  echo "----------------------------"
  exit 0
else
  echo "----------------------------------------------"
  echo "This tests(s) was failed: $was_failed. Check logs and reports"
  echo "----------------------------------------------"
  exit 1
fi
