#!/usr/bin/env bash
set -euo pipefail

KNOWN_SERVICES=(
  gateway-service
  user-service
  auth-service
  cat-service
  storage-service
  notification-service
  adoption-service
  form-analysis-service
  organization-service
  chat-service
)

PIDS=()
RUN_E2E=false

cleanup() {
  echo ""
  echo "Stopping services..."
  for pid in "${PIDS[@]}"; do
    kill -TERM "$pid" 2>/dev/null || true
  done
  for pid in "${PIDS[@]}"; do
    wait "$pid" 2>/dev/null || true
  done
  echo "All services stopped."
  exit 0
}

trap cleanup SIGINT SIGTERM

is_known_service() {
  local svc="$1"
  for known in "${KNOWN_SERVICES[@]}"; do
    [[ "$known" == "$svc" ]] && return 0
  done
  return 1
}

start_service() {
  local svc="$1"
  mvn compile quarkus:dev -pl "$svc" -am &
  PIDS+=($!)
}

wait_for_stack() {
  local ports=(8080 8081 8082 8083 8084 8085 8086 8087 8088 8089)
  local expected=${#ports[@]}
  echo "Waiting for all $expected services to be ready..."
  for i in $(seq 1 60); do
    local up=0
    for port in "${ports[@]}"; do
      code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 2 "http://localhost:$port/q/health/live" 2>/dev/null)
      [[ "$code" == "200" ]] && up=$((up + 1))
    done
    echo "  [$i/60] $up/$expected ready"
    [[ "$up" -eq "$expected" ]] && return 0
    sleep 10
  done
  echo "Timed out waiting for stack." >&2
  return 1
}

# Parse arguments: optional --e2e flag and optional service list
SERVICE_ARG=""
for arg in "$@"; do
  if [[ "$arg" == "--e2e" ]]; then
    RUN_E2E=true
  else
    SERVICE_ARG="$arg"
  fi
done

if [[ -z "$SERVICE_ARG" ]]; then
  SERVICES=("${KNOWN_SERVICES[@]}")
else
  IFS=',' read -ra REQUESTED <<< "$SERVICE_ARG"

  for svc in "${REQUESTED[@]}"; do
    if ! is_known_service "$svc"; then
      echo "Error: unknown service '$svc'." >&2
      echo "Known services: ${KNOWN_SERVICES[*]}" >&2
      exit 1
    fi
  done

  SERVICES=(gateway-service)
  for svc in "${REQUESTED[@]}"; do
    [[ "$svc" != "gateway-service" ]] && SERVICES+=("$svc")
  done
fi

echo "Starting services: ${SERVICES[*]}"

for svc in "${SERVICES[@]}"; do
  start_service "$svc"
done

if $RUN_E2E; then
  wait_for_stack
  echo ""
  echo "Running e2e tests..."
  mvn test -Pe2e -pl e2e-tests
  E2E_EXIT=$?
  cleanup
  exit $E2E_EXIT
fi

wait