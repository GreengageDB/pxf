#!/usr/bin/env bash
set -e
# --- Declarations ---
export GROUP=${1:-$GROUP}     # Required. Checking below
export USE_FDW=${2:-$USE_FDW} # use `external-table` if not defined
export USE_SSL=${3:-$USE_SSL} # used in compose.sh

export TEST_SERVICE=${TEST_SERVICE:-mdw}
export ARTIFACTS=${ARTIFACTS:-artifacts}
export DEBUG_DIR=${DEBUG_DIR:-$ARTIFACTS/docker_logs}

# --- Prepare ---
mkdir -p "$DEBUG_DIR"

# Set a variable to check the results of all tests at the end of the script
test_result_status=0

# --- Functions ---
# shellcheck disable=SC2329
trap_exit() {
  if [ -n "$DEBUG" ]; then
    DEBUG_DIR=${DEBUG_DIR:-'/tmp'} # if EXIT early when definitions completed
    mkdir -p "$DEBUG_DIR"
    docker compose logs >> "$DEBUG_DIR/compose_before_exit.log"
    journalctl -exu docker >> "$DEBUG_DIR/docker.log"
    journalctl -exu containerd >> "$DEBUG_DIR/containerd.log"
  fi
} ; trap trap_exit EXIT

start_copy_artifacts() {
  local test=$1
  local table_type=$2
  local test_dir
  echo "-------------------------------------"
  echo "Start copy artifacts for $test ($table_type)"
  echo "-------------------------------------"
  test_dir="$ARTIFACTS/$test/$table_type"
  mkdir -p "$test_dir"
  docker compose cp "$TEST_SERVICE:/home/gpadmin/workspace/pxf/automation/target/surefire-reports" "./$test_dir"
  docker compose cp "$TEST_SERVICE:/home/gpadmin/workspace/pxf/automation/sqlrepo" "./$test_dir"
  docker compose cp "$TEST_SERVICE:/home/gpadmin/workspace/pxf/automation/automation_logs" "./$test_dir"
  docker compose cp "$TEST_SERVICE:/home/gpadmin/workspace/pxf/automation/target/allure-results" "./$test_dir"
  pxf_log_count=$(docker compose exec -it "$TEST_SERVICE" ls  /tmp/pxf 2> /dev/null | wc -l)
  if [ "$pxf_log_count" -ge 1 ]; then
    docker compose cp "$TEST_SERVICE:/tmp/pxf" ./$test_dir
  fi
}

check_test_result() {
  local exit_code=$1
  local test_group=$2
  local table_type=$3
  if [ "$exit_code" -eq "0" ]; then
    echo "------------------------------------------------------"
    echo "Test for the group '$test_group' ($table_type) finished with SUCCESS"
    echo "------------------------------------------------------"
  else
    echo "----------------------------------------------------"
    echo "Test for the group '$test_group' ($table_type) finished with ERROR"
    echo "----------------------------------------------------"
    test_result_status=1
  fi
}

# --- Body ---
if [ -z "$GROUP" ] ; then # Variable GROUP required
  echo -en "GROUP required.\nUse shell environment variable or first parameter.\nExample: '$0 smoke' or 'GROUP=smoke $0'\n"
  exit 1
fi

# shellcheck disable=SC2155
export TYPE=$([ -n "$USE_FDW" ] && echo -n "fdw" || echo -n "external-table")
echo -en "-----\n----- Start running '$GROUP' tests with $TYPE\n-----\n"

bash compose.sh up

[ -n "$DEBUG" ] && echo "Run 'docker compose exec \"$TEST_SERVICE\" sudo -H -u gpadmin bash -l -c \"make -C \$TEST_HOME GROUP=$GROUP USE_FDW=$USE_FDW\"'" | tee -a "$DEBUG_DIR/compose_before_down.log" || true
docker compose exec "$TEST_SERVICE" sudo -H -u gpadmin bash -l -c "make -C \$TEST_HOME GROUP=$GROUP USE_FDW=$USE_FDW"
check_test_result $? "$GROUP" "$$TYPE"
start_copy_artifacts "$GROUP" "$$TYPE"

echo "-------------------------"
echo "Check tests result status"
echo "-------------------------"
if [ "$test_result_status" -eq "0" ]; then
  echo "----------------"
  echo "All tests passed"
  echo "----------------"
  exit 0
else
  echo "----------------------------------------------"
  echo "Some tests didn't pass. Check logs and reports"
  echo "----------------------------------------------"
  exit 1
fi
