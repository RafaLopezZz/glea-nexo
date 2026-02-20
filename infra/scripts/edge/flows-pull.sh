#!/usr/bin/env bash
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"

SRC="/opt/glea-nexo/edge-data/nodered/flows.json"
DST_VER="$REPO_ROOT/edge/nodered/flows/flows.json"
DST_DEV="$REPO_ROOT/edge/nodered/data/flows.json"

mkdir -p "$(dirname "$DST_VER")" "$(dirname "$DST_DEV")"
cp -a "$SRC" "$DST_VER"
cp -a "$DST_VER" "$DST_DEV"

echo "OK pulled -> $DST_VER"
sha256sum "$DST_VER"
