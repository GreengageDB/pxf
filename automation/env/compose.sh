#!/usr/bin/env bash
set -e

# --- Declarations ---
export DOCKERCOMPOSEBIN="docker compose --project-name ${PROJECT:=pxf-it} --profile ${PROFILE:=all}"
export DEBUG_DIR=${DEBUG_DIR:-artifacts/docker_logs}
export SCRIPT_DIR=${SCRIPT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)}

# --- Prepare ---
mkdir -p "$DEBUG_DIR"

# --- Functions ---
compose_up() {
  local compose_file="$SCRIPT_DIR/${1:-docker-compose${USE_SSL:+-ssl}.yaml}"
  [ -n "$DEBUG" ] && echo "Starting Docker compose with '$compose_file'" || true
   COMPOSE_HTTP_TIMEOUT=300 $DOCKERCOMPOSEBIN -f "$compose_file" up  --quiet-pull --no-deps -d --remove-orphans
}

compose_down() {
  [ -n "$DEBUG" ] && echo -en "Try to stop Docker Composes project '$PROJECT'... " || true
  COMPOSE_HTTP_TIMEOUT=300 $DOCKERCOMPOSEBIN down || true
} ; export -f compose_down

check_docker_container_status() {
  local i unhealthy_containers container_ids container_id container_name status
  for i in {01..99}; do
    local log_file="$DEBUG_DIR/compose_status_attempt_$i.log"
    unset unhealthy_containers
    echo "-------------------------------------------"
    echo "Check docker containers status (attempt $i)"
    echo "-------------------------------------------"
    container_ids=$($DOCKERCOMPOSEBIN ps -q)
    [ -n "$DEBUG" ] && echo -n "--- DEBUG --- " && echo -e "Attempt $i --- Found container(s) ---\n$container_ids\n" | tee "$log_file" || true
    for container_id in $container_ids
    do
      [ -n "$DEBUG" ] && echo -n "--- DEBUG --- " && echo "Processing ID: '$container_id'" | tee -a "$log_file" || true
      container_name=$(docker container ls --all --no-trunc --filter "id=$container_id" --format "{{.Names}}")
      [ -n "$DEBUG" ] && echo -n "--- DEBUG --- " && echo "Processing Name: '$container_name'" | tee -a "$log_file" || true
      status=$(docker inspect "$container_id" --format "{{.State.Health.Status}}")
      [ -n "$DEBUG" ] && echo -n "--- DEBUG --- " && echo "Status: '$status'" | tee -a "$log_file" || true
      if [[ "$status" != 'healthy' ]]; then unhealthy_containers=${unhealthy_containers:+$unhealthy_containers, }$container_name ; fi
    done
    [ -n "$DEBUG" ] && $DOCKERCOMPOSEBIN logs >> "$log_file" || true
    if [ -n "$unhealthy_containers" ]; then
      echo "Conatainer(s) $unhealthy_containers still unhealthy. Waiting..."
      sleep 15
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

case $1 in
    up)
        compose_up "$2"
        check_docker_container_status
        ;;
    down)
        compose_down
        ;;
    *)
        echo "Error: Unknown or absent command '$1'"
        echo "Usage: $0 {up|down} [<docker_compose_file_path>]"
        exit 1
        ;;
esac
