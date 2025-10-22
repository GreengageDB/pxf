#!/usr/bin/env bash
set -e

# --- Declarations ---
export DOCKERCOMPOSEBIN='docker compose'

# --- Functions ---
compose_up() {
  local compose_file="${1:-docker-compose.yaml}"
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
    container_ids=$(docker compose ps -q)
    for container_id in $container_ids
    do
      status=$(docker inspect "$container_id" --format "{{.State.Health.Status}}")
      if [ "$status" != "healthy" ]; then
        docker_name=$(docker container ls --all --no-trunc --filter "id=$container_id" --format "{{.Names}}")
        [ -n "$DEBUG" ] && echo "Container $docker_name unhealty after $i attempt(s)" > "artifacts/compose_unhealthy_$docker_name.log" || true
        [ -n "$DEBUG" ] && docker compose logs "$docker_name" >> "artifacts/compose_unhealthy_$docker_name.log" || true
        if [[ "$docker_name" != "oracle" && "$docker_name" != "mysql" ]]; then
          unhealthy_present="true"
          echo "Container '$docker_name' is not in a healthy status yet. Current status is '$status'."
        else
          [ -n "$DEBUG" ] && echo "Container $docker_name healty after $i attempt(s)" > "artifacts/compose_healthy_$docker_name.log" || true
          [ -n "$DEBUG" ] && docker compose logs "$docker_name" >> "artifacts/compose_healthy_$docker_name.log" || true
          if [ "$check_oracle_service_health" == "true" ]; then
            unhealthy_present="true"
            echo "Container '$docker_name' is not in a healthy status yet. Current status is '$status'."
          fi
        fi
      fi
    if [ -n "$DEBUG" ] ; then
      cat > "artifacts/compose_status_attempt_$i.log" <<EOF
      -----------------------------------
      Check docker containers status (attempt $i)
      -----------------------------------
EOF
      docker compose logs >> "artifacts/compose_status_attempt_$i.log"
      journalctl -exu docker > artifacts/docker_status.log
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

case $1 in
    up)
        compose_up "$2"
        check_docker_container_status false
        ;;
    down)
        [ -n "$DEBUG" ] && mkdir -p artifacts
        [ -n "$DEBUG" ] && docker compose logs >> artifacts/compose_before_down.log || true
        compose_down
        ;;
    *)
        echo "Error: Unknown or absent command '$1'"
        echo "Usage: $0 {up|down} [<docker_compose_file_path>]"
        exit 1
        ;;
esac
