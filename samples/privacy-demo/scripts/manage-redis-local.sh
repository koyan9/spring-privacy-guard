#!/usr/bin/env bash

set -euo pipefail

ACTION="${1:-up}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SAMPLE_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${SAMPLE_DIR}/docker-compose.redis.yml"

if [[ ! -f "${COMPOSE_FILE}" ]]; then
  echo "Compose file not found: ${COMPOSE_FILE}" >&2
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker CLI was not found on PATH. Install Docker or run Redis manually on localhost:6379." >&2
  exit 1
fi

if docker compose version >/dev/null 2>&1; then
  DOCKER_CMD=(docker compose -f "${COMPOSE_FILE}")
elif command -v docker-compose >/dev/null 2>&1; then
  DOCKER_CMD=(docker-compose -f "${COMPOSE_FILE}")
else
  echo "Neither 'docker compose' nor 'docker-compose' is available." >&2
  exit 1
fi

case "${ACTION}" in
  up)
    "${DOCKER_CMD[@]}" up -d
    ;;
  down)
    "${DOCKER_CMD[@]}" down
    ;;
  logs)
    "${DOCKER_CMD[@]}" logs --tail 100
    ;;
  ps)
    "${DOCKER_CMD[@]}" ps
    ;;
  *)
    echo "Unsupported action: ${ACTION}. Use one of: up, down, logs, ps." >&2
    exit 1
    ;;
esac
