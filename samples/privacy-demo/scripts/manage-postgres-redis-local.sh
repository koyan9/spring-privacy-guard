#!/usr/bin/env bash
set -euo pipefail

ACTION="${1:-up}"
COMPOSE_FILE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/docker-compose.postgres-redis.yml"

if [[ "$ACTION" == "down" ]]; then
  docker compose -f "$COMPOSE_FILE" down --remove-orphans
  exit 0
fi

docker compose -f "$COMPOSE_FILE" up -d --build
