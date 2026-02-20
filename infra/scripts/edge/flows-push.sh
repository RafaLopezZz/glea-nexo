#!/usr/bin/env bash
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"

SRC_VER="$REPO_ROOT/edge/nodered/flows/flows.json"
DST="/opt/glea-nexo/edge-data/nodered/flows.json"

cp -a "$SRC_VER" "$DST"
echo "OK pushed -> $DST"
sha256sum "$DST"

if [ "${1:-}" = "--restart" ]; then
  docker compose -f "$REPO_ROOT/infra/compose/docker-compose.edge.yml" -f "$REPO_ROOT/infra/compose/docker-compose.edge.rpi.yml" restart nodered
fi
