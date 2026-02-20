# ITER-EDGE-001 — Sync Edge runtime a Git (flows + schema + scripts)

Fecha: 2026-02-20

## Fuente de verdad (Edge)
- Node-RED runtime: `/opt/glea-nexo/edge-data/nodered/flows.json`
- Credenciales Node-RED: `/opt/glea-nexo/edge-data/nodered/flows_cred.json` (NO versionar)
- SQLite runtime: `/opt/glea-nexo/edge-data/sqlite/edge.db` (NO versionar)

## Repo
- Flow versionable: `edge/nodered/flows/flows.json`
- Flow dev/local: `edge/nodered/data/flows.json`
- DDL: `edge/sqlite/schema.sql`

## Compose
- Base portable: `infra/compose/docker-compose.edge.yml` (rutas repo)
- Override RPi: `infra/compose/docker-compose.edge.rpi.yml` (rutas /opt)

## Scripts
- `infra/scripts/edge/flows-pull.sh`
- `infra/scripts/edge/flows-push.sh [--restart]`
