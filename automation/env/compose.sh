#!/usr/bin/env bash
set -e

# --- Declarations ---
export DOCKERCOMPOSEBIN="docker compose --profile ${PROFILE:=all}"
export DEBUG_DIR=${DEBUG_DIR:-artifacts/docker_logs}

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
  local check_oracle_service_health=$1 # Whether the oracle service should be healthy immediately or not
  for i in {01..20}; do
    unhealthy_present="false"
    echo "-----------------------------------"
    echo "Check docker containers status (attempt $i)"
    echo "-----------------------------------"
    container_ids=$($DOCKERCOMPOSEBIN ps -q)
    for container_id in $container_ids
    do
      [ -n "$DEBUG" ] && echo "Container $docker_name $status after $i attempt(s)" > "$DEBUG_DIR/compose_${status}_$docker_name.log" || true
      [ -n "$DEBUG" ] && $DOCKERCOMPOSEBIN logs "$docker_name" >> "$DEBUG_DIR/compose_${status}_$docker_name.log" || true
      status=$(docker inspect "$container_id" --format "{{.State.Health.Status}}")
      if [ "$status" != "healthy" ]; then
        docker_name=$(docker container ls --all --no-trunc --filter "id=$container_id" --format "{{.Names}}")
        if [[ "$docker_name" != "oracle" ]]; then
          unhealthy_present="true"
          echo "Container '$docker_name' is not in a healthy status yet. Current status is '$status'."
        else
          if [ "$check_oracle_service_health" == "true" ]; then
            unhealthy_present="true"
            echo "Container '$docker_name' is not in a healthy status yet. Current status is '$status'."
          fi
        fi
      fi
    if [ -n "$DEBUG" ] ; then
      cat > "$DEBUG_DIR/compose_status_attempt_$i.log" <<EOF
      -----------------------------------
      Check docker containers status (attempt $i)
      -----------------------------------
EOF
      $DOCKERCOMPOSEBIN logs >> "$DEBUG_DIR/compose_status_attempt_$i.log"
      journalctl -exu docker > "$DEBUG_DIR/docker_status.log"
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
      $DOCKERCOMPOSEBIN ps
      exit 1
  fi
}

case $1 in
    up)
        compose_up "$2"
        check_docker_container_status true # we need to check oracle
        ;;
    down)
        [ -n "$DEBUG" ] && mkdir -p "$DEBUG_DIR"
        [ -n "$DEBUG" ] && $DOCKERCOMPOSEBIN logs >> "$DEBUG_DIR/compose_before_down.log" || true
        compose_down
        ;;
    *)
        echo "Error: Unknown or absent command '$1'"
        echo "Usage: $0 {up|down} [<docker_compose_file_path>]"
        exit 1
        ;;
esac
