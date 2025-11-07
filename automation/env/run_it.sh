#!/usr/bin/env bash
set -eu

BUILD_IMAGES=$1
profile=${2:-${PROFILE:-all}}
run_test_service_name=mdw

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
  bash compose.sh down
} ; trap trap_exit EXIT

if [ "$BUILD_IMAGES" == "true" ]; then
  echo "------------"
  echo "Build images"
  echo "------------"
  bash build-images.sh
fi

echo "----------------"
echo "Start containers without SSL"
echo "----------------"
docker-compose --profile ${profile} up -d

function check_docker_container_status() {
  local check_oracle_service_health=$1 # Whether the oracle service should be healthy immediately or not
  for i in {1..120}; do
    unhealthy_present="false"
    echo "-----------------------------------"
    echo "Check docker containers status: $i"
    echo "-----------------------------------"
    container_ids=$(docker-compose --profile ${profile} ps -q)
    for container_id in $container_ids
    do
      status=$(docker inspect $container_id --format "{{.State.Health.Status}}")
      if [ "$status" != "healthy" ]; then
        docker_name=$(docker container ls --all --no-trunc --filter "id=$container_id" --format "{{.Names}}")
        if [ "$docker_name" != "oracle" ]; then
          unhealthy_present="true"
          echo "Container '$docker_name' is not in a healthy status yet. Current status is '$status'."
        else
          if [ "$check_oracle_service_health" == "true" ]; then
            unhealthy_present="true"
            echo "Container '$docker_name' is not in a healthy status yet. Current status is '$status'."
          fi
        fi
      fi
    done
    if [ "$unhealthy_present" == "true" ]; then
      sleep 10
    else
      echo "---------------------------------------"
      echo "All containers are in the healthy state"
      echo "---------------------------------------"
      break;
    fi
  done

  if [ "$unhealthy_present" == "true" ]; then
      echo "--------------------------------------------"
      echo "Some containers are not in the healthy state"
      echo "--------------------------------------------"
      docker-compose --profile ${profile} ps
      exit 1
  fi
}

bash compose.sh down

echo "----------------"
echo "Start containers without SSL"
echo "----------------"
bash compose.sh up

echo "----------------"
echo "Run tests '$TESTS' with FDW"
echo "----------------"

export USE_FDW=true
for test in $TESTS ; do
  GROUP=$test bash it.sh
done

bash compose.sh down

echo "----------------"
echo "Start containers with SSL"
echo "----------------"
bash compose.sh up docker-compose-ssl.yaml

echo "------------------"
echo "Restart containers"
echo "------------------"
docker-compose --profile ${profile} down
docker-compose --profile ${profile} up -d
check_docker_container_status false # We don't need oracle service for FDW tests

echo "----------------------------------"
echo "Start running smoke tests with FDW"
echo "----------------------------------"
docker-compose exec $run_test_service_name sudo -H -u gpadmin bash -l -c 'pushd $TEST_HOME && make GROUP=smoke USE_FDW=true'
check_test_result $? smoke fdw
start_copy_artifacts smoke fdw

echo "--------------------------------------------------------"
echo "Start running integration tests in 'gpdb' group with FDW"
echo "--------------------------------------------------------"
docker-compose exec $run_test_service_name sudo -H -u gpadmin bash -l -c 'pushd $TEST_HOME && make GROUP=gpdb USE_FDW=true'
check_test_result $? gpdb fdw
start_copy_artifacts gpdb fdw

echo "-------------------------------------------------------------"
echo "Start running integration tests in 'jdbc' group with FDW"
echo "-------------------------------------------------------------"
docker-compose exec $run_test_service_name sudo -H -u gpadmin bash -l -c 'pushd $TEST_HOME && make GROUP=jdbc USE_FDW=true'
check_test_result $? jdbc fdw
start_copy_artifacts jdbc fdw

echo "------------------"
echo "Stop containers and start containers with ssl"
echo "------------------"
docker-compose --profile ${profile} down
docker-compose -f docker-compose-ssl.yaml up -d
check_docker_container_status false # We don't need oracle service ssl tests

echo "------------------------------------------------------------------------"
echo "Start running integration tests in 'ggdbssl' group with FDW"
echo "------------------------------------------------------------------------"
docker-compose exec $run_test_service_name sudo -H -u gpadmin bash -l -c 'pushd $TEST_HOME && make GROUP=ggdbssl USE_FDW=true'
echo "Start running integration tests in 'ggdbssl' group with FDW"
check_test_result $? ggdbssl fdw
start_copy_artifacts ggdbssl fdw

echo "-------------------"
echo "Shutdown containers"
echo "-------------------"
docker-compose -f docker-compose-ssl.yaml down

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
