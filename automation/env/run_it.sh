#!/usr/bin/env bash

# shellcheck disable=SC2329
save_logs() {
    docker compose logs > artifacts/compose_before_exit.log
    journalctl -exu docker > artifacts/docker.log
} ; trap save_logs EXIT

build_images=$1
run_test_service_name=mdw
compose_name=integration_tests

mkdir -p artifacts

# Set a variable to check the results of all tests at the end of the script
test_result_status=0

if [ "$build_images" == "true" ]; then
  echo "------------"
  echo "Build images"
  echo "------------"
  bash build-images.sh
fi

echo "----------------"
echo "Start containers"
echo "----------------"
docker compose -p $compose_name up -d

function check_docker_container_status() {
  local check_oracle_service_health=$1 # Whether the oracle service should be healthy immediately or not
  for i in {1..120}; do
    unhealthy_present="false"
    echo "-----------------------------------"
    echo "Check docker containers status: $i"
    echo "-----------------------------------"
    container_ids=$(docker compose ps -q)
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
      docker compose ps
      exit 1
  fi
}

start_copy_artifacts() {
  local test=$1
  local table_type=$2
  echo "-------------------------------------"
  echo "Start copy artifacts for $test ($table_type)"
  echo "-------------------------------------"
  test_dir=artifacts/$test/$table_type
  mkdir -p $test_dir
  docker compose cp $run_test_service_name:/home/gpadmin/workspace/pxf/automation/target/surefire-reports ./$test_dir
  docker compose cp $run_test_service_name:/home/gpadmin/workspace/pxf/automation/sqlrepo ./$test_dir
  docker compose cp $run_test_service_name:/home/gpadmin/workspace/pxf/automation/automation_logs ./$test_dir
  docker compose cp $run_test_service_name:/home/gpadmin/workspace/pxf/automation/target/allure-results ./$test_dir
  pxf_log_count=$(docker compose exec -it $run_test_service_name ls  /tmp/pxf 2> /dev/null | wc -l)
  if [ "$pxf_log_count" -ge 1 ]; then
    docker compose cp $run_test_service_name:/tmp/pxf ./$test_dir
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

check_docker_container_status false # We don't need oracle service immediately

echo "---------------------------------------------"
echo "Start running smoke tests with external table"
echo "---------------------------------------------"
docker compose exec $run_test_service_name sudo -H -u gpadmin bash -l -c 'pushd $TEST_HOME && make GROUP=smoke'
check_test_result $? smoke external-table
start_copy_artifacts smoke external-table

echo "-------------------------------------------------------------------"
echo "Start running integration tests in 'gpdb' group with external table"
echo "-------------------------------------------------------------------"
docker compose exec $run_test_service_name sudo -H -u gpadmin bash -l -c 'pushd $TEST_HOME && make GROUP=gpdb'
check_test_result $? gpdb external-table
start_copy_artifacts gpdb external-table

echo "------------------------------------------------------------------------"
echo "Start running integration tests in 'jdbc' group with external table"
echo "------------------------------------------------------------------------"
check_docker_container_status true # We need oracle service to be healthy for this group of tests
docker compose exec $run_test_service_name sudo -H -u gpadmin bash -l -c 'pushd $TEST_HOME && make GROUP=jdbc'
check_test_result $? jdbc external-table
start_copy_artifacts jdbc external-table

echo "------------------"
echo "Restart containers"
echo "------------------"
docker compose logs > artifacts/compose_before_restart.log
docker compose down
docker compose up -d
check_docker_container_status false # We don't need oracle service for FDW tests

echo "----------------------------------"
echo "Start running smoke tests with FDW"
echo "----------------------------------"
docker compose exec $run_test_service_name sudo -H -u gpadmin bash -l -c 'pushd $TEST_HOME && make GROUP=smoke USE_FDW=true'
check_test_result $? smoke fdw
start_copy_artifacts smoke fdw

echo "--------------------------------------------------------"
echo "Start running integration tests in 'gpdb' group with FDW"
echo "--------------------------------------------------------"
docker compose exec $run_test_service_name sudo -H -u gpadmin bash -l -c 'pushd $TEST_HOME && make GROUP=gpdb USE_FDW=true'
check_test_result $? gpdb fdw
start_copy_artifacts gpdb fdw

echo "-------------------------------------------------------------"
echo "Start running integration tests in 'jdbc' group with FDW"
echo "-------------------------------------------------------------"
docker compose exec $run_test_service_name sudo -H -u gpadmin bash -l -c 'pushd $TEST_HOME && make GROUP=jdbc USE_FDW=true'
check_test_result $? jdbc fdw
start_copy_artifacts jdbc fdw

echo "------------------"
echo "Stop containers and start containers with ssl"
echo "------------------"
docker compose logs > artifacts/compose_before_ssl.log
docker compose down
docker compose -f docker-compose-ssl.yaml up -d
check_docker_container_status false # We don't need oracle service ssl tests

echo "------------------------------------------------------------------------"
echo "Start running integration tests in 'ggdbssl' group with FDW"
echo "------------------------------------------------------------------------"
docker compose exec $run_test_service_name sudo -H -u gpadmin bash -l -c 'pushd $TEST_HOME && make GROUP=ggdbssl USE_FDW=true'
echo "Start running integration tests in 'ggdbssl' group with FDW"
check_test_result $? ggdbssl fdw
start_copy_artifacts ggdbssl fdw

echo "-------------------"
echo "Shutdown containers"
echo "-------------------"
docker compose logs > artifacts/compose_before_down.log
docker compose -f docker-compose-ssl.yaml down

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
