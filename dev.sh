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
)

PIDS=()

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

if [[ $# -eq 0 ]]; then
  SERVICES=("${KNOWN_SERVICES[@]}")
else
  IFS=',' read -ra REQUESTED <<< "$1"

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

wait