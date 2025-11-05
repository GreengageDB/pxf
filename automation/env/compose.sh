#!/usr/bin/env bash
set -e

# --- Declarations ---
export DOCKERCOMPOSEBIN="docker compose --profile ${PROFILE:=all}"
export DEBUG_DIR=${DEBUG_DIR:-artifacts/docker_logs}

# --- Prepare ---
[ -n "$DEBUG" ] && mkdir -p "$DEBUG_DIR" || true

# --- Functions ---
compose_up() {
  local compose_file="${1:-docker-compose${USE_SSL:+-ssl}.yaml}"
  [ -n "$DEBUG" ] && echo "Starting Docker compose with '$compose_file'" || true
   COMPOSE_HTTP_TIMEOUT=300 $DOCKERCOMPOSEBIN -f "$compose_file" up  --quiet-pull --no-deps -d --remove-orphans
}

compose_down() {
  [ -n "$DEBUG" ] && echo -en 'Find working Docker Composes... ' || true
  COMPOSES=$($DOCKERCOMPOSEBIN ls -q)
  if [ -n "$COMPOSES" ]; then
    [ -n "$DEBUG" ] && echo "found: '$COMPOSES'" || true
    for COMPOSE in $COMPOSES; do
      [ -n "$DEBUG" ] && echo "Stopping $COMPOSE" || true
      COMPOSE_HTTP_TIMEOUT=300 $DOCKERCOMPOSEBIN -p $COMPOSE down
    done
  else
      [ -n "$DEBUG" ] && echo 'found nothing' || true
  fi
} ; export -f compose_down

check_docker_container_status() {
  local i unhealthy_containers container_ids container_id container_name status
  for i in {01..20}; do
    local log_file="$DEBUG_DIR/compose_status_attempt_$i.log"
    unset unhealthy_containers
    echo "-------------------------------------------"
    echo "Check docker containers status (attempt $i)"
    echo "-------------------------------------------"
    container_ids=$($DOCKERCOMPOSEBIN ps -q)
    [ -n "$DEBUG" ] && echo -n "--- DEBUG --- " && echo "Attempt $i -- found containers: '$container_ids'" | tee "$log_file" || true
    for container_id in $container_ids
    do
      [ -n "$DEBUG" ] && echo -n "--- DEBUG --- " && echo "Processing ID: '$container_id'" | tee -a "$log_file" || true
      container_name=$(docker container ls --all --no-trunc --filter "id=$container_id" --format "{{.Names}}")
      [ -n "$DEBUG" ] && echo -n "--- DEBUG --- " && echo "Processing Name: '$container_name'" | tee -a "$log_file" || true
      if [[ "$container_name" == 'oracle' ]]; then continue ; fi # skip oracle
      status=$(docker inspect "$container_id" --format "{{.State.Health.Status}}")
      [ -n "$DEBUG" ] && echo -n "--- DEBUG --- " && echo "Status: '$status'" | tee -a "$log_file" || true
      if [[ "$status" != 'healthy' ]]; then unhealthy_containers=${unhealthy_containers:+$unhealthy_containers, }$container_name ; fi
    done
    $DOCKERCOMPOSEBIN logs >> "$log_file"
    if [ -n "$unhealthy_containers" ]; then
      echo "Conatainer(s) $unhealthy_containers still unhealthy. Waiting..."
      sleep 10
    else
      echo "---------------------------------------------------------"
      echo "All containers are in the healthy state after $i attempts"
      echo "---------------------------------------------------------"
      break;
    fi
  done

  if [ -n "$unhealthy_containers" ]; then
      echo "--------------------------------------------"
      echo "Conatainer(s) $unhealthy_containers are not in the healthy state after $i attempts"
      echo "--------------------------------------------"
      $DOCKERCOMPOSEBIN ps
      exit 1
  fi
}

check_oracle_status() {
  local i unhealthy_oracle status
  local container_name='oracle'
  for i in {01..20}; do
    unset unhealthy_oracle
    echo "-----------------------------------"
    echo "Check $container_name containers status (attempt $i)"
    echo "-----------------------------------"
    status=$(docker inspect "$container_name" --format "{{.State.Health.Status}}")
    if [ "$status" != 'healthy' ]; then
      unhealthy_oracle="$container_name"
      echo "Container '$container_name' is not in a healthy status yet. Current status is '$status'."
      sleep 30
    else
      echo "---------------------------------------"
      echo "$container_name is in the healthy state"
      echo "---------------------------------------"
      break;
    fi
  done

  if [ -n "$unhealthy_oracle" ]; then
      echo "--------------------------------------------"
      echo "$container_name is not in the healthy state after $i attempts"
      echo "--------------------------------------------"
      $DOCKERCOMPOSEBIN ps
      exit 1
  fi
}


case $1 in
    up)
        compose_up "$2"
        check_docker_container_status
        [[ "$PROFILE" == 'all' || "$PROFILE" == 'jdbc' ]] && check_oracle_status || true # Additionally check Oracle for specific profiles
        ;;
    down)
        [ -n "$DEBUG" ] && $DOCKERCOMPOSEBIN logs >> "$DEBUG_DIR/compose_before_down.log" || true
        compose_down
        ;;
    *)
        echo "Error: Unknown or absent command '$1'"
        echo "Usage: $0 {up|down} [<docker_compose_file_path>]"
        exit 1
        ;;
esac
