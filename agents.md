# AGENTS.md

Guía operativa para OpenCode en este repo. Solo lo que un agente probablemente se equivocaría sin ayuda.

## TL;DR

```
# Build & up (edge + platform juntos)
docker compose -f infra/compose/docker-compose.edge.yml -f infra/compose/docker-compose.platform.yml up -d

# Check
curl -s http://localhost:8080/api/ping
curl -s http://localhost:8080/actuator/health
curl -s http://localhost:4200
curl -s http://localhost:1880

# Tests backend
cd backend && mvn test

# Publicar telemetría de prueba
docker compose -f infra/compose/docker-compose.edge.yml exec mosquitto \
  mosquitto_pub -h localhost -t "agro/finca1/zona1/pi-gw-001/sensor/soil-01/SOIL_MOISTURE/telemetry" \
  -m '{"messageId":"t-001","deviceId":"pi-gw-001","sensorId":"soil-01","type":"SOIL_MOISTURE","ts":"2026-01-01T00:00:00Z","value":25.0,"unit":"%VWC","quality":"good"}'

# Logs
docker compose -f infra/compose/docker-compose.platform.yml logs -f backend
```

## Puertos reales (lo que no está en docs viejos)

| Servicio         | Interno  | Host     | Nota                          |
|------------------|----------|----------|-------------------------------|
| Backend          | 8081     | 8080     | El healthcheck usa 8081       |
| PostgreSQL       | 5432     | 3609     | **NO 3608** (cambiado)        |
| Frontend         | 4200     | 4200     | ng serve con polling          |
| Mosquitto        | 1883     | 1883     | anónimo, sin TLS              |
| Node-RED         | 1880     | 1880     |                              |

Postgres creds: `glea` / `glea_123`, db `glea_nexo`.

## API — contratos que un agente rompería

- **`X-Org-Code`** header opcional (default `"default"`). Sin él → org `default`. Si esa org no existe → 404.
- **`deviceUid`** es inmutable tras creación. `PUT /api/devices/{id}` solo acepta `name` y `state`. Enviar `deviceUid` → 400.
- **`spring.jackson.deserialization.fail-on-unknown-properties=true`**. Propiedades extra en request body → 400.
- **Error estándar**: `{ "error": "CONFLICT", "status": 409, "correlationId": "..." }`.
- **Ingest batch**: `POST /api/ingest/readings/batch` — body con array `readings`, cada uno con `messageId` único. Backend responde `{inserted, duplicates, rejected}`.
- **Telemetría consulta**: `GET /api/telemetry/readings?zoneId&deviceId&from&to`.
- **Swagger**: `http://localhost:8080/swagger-ui/index.html`.
- **Securidad**: `SecurityConfig.java` permite todo (JWT no implementado). CORS permite `localhost:*`.

## Backend

- **Stack**: Spring Boot 3.5.0, Java 21, Spring Data JPA, Flyway + ddl-auto=update (ambos activos).
- **Flyway**: `classpath:db/migration/`, baseline-on-migrate=true (V1__ ya aplicada).
- **TestContainers**: todas las integration tests requieren Docker en ejecución.
- **Logs**: nivel DEBUG para `com.glea.nexo`, correlationId en contexto via `X-Correlation-Id` header.
- **Log archivo**: `/app/logs/glea-nexo.log` (rolling diario, 30 días retención).
- **Test unitario**: `TopicParserTest` sin Docker.
- **Test integración**: `*IntegrationTest.java` con TestContainers PostgreSQL.
- **MOD**: `backend/src/main/java/com/glea/nexo/` — paquetes `api`, `application`, `domain`, `config`, `security`.
- **Entidades clave**: `Farm`, `Zone`, `Device`, `Sensor`, `SensorType`, `Unit`, `TelemetryReading`, `IngestEvent`, `Organization`.

## Frontend

- **Stack**: Angular 17 standalone, signals, `@angular/common/http`.
- **Sin router**: todo en `AppComponent` (single-page SPA).
- **API base URL**: lee `window.__GLEA_CONFIG__?.apiBaseUrl || 'http://localhost:8080/api'`.
- **Pruebas**: Karma + Jasmine. `ng test` (requiere Chrome).
- **Package manager**: npm.
- **Sin routing**: ni módulos de features. Es un solo componente con template condicional.

## Edge — Node-RED

- **Flow source (git)**: `edge/nodered/flows/flows.json` — editar aquí.
- **Runtime**: `edge/nodered/data/flows.json` (montado como `/data/flows.json` en contenedor).
- **SQLite buffer**: `edge/sqlite/schema.sql` define tabla `outbox`. Se monta como `/data/sqlite/`.
- **Flow activo**: 4 tabs — Flujo 1 (debug/outbox setup), Flujo 2 (MQTT ingest → outbox con INSERT OR IGNORE), Flujo 3 (manual test), Bridge MQTT→Backend (filtra telemetry → HTTP POST batch).
- **Bridge**: suscribe `agro/#`, parsea topic v2, normaliza unidades, POST a `http://192.168.1.10:8080/api/ingest/readings/batch`.
- **En vars de entorno**: se pueden pasar para configurar URL del backend, etc.

## Edge — Python Simulator

- **Ubicación**: `edge/python/services/simulator/main.py`.
- **Dependencia**: `paho-mqtt==1.6.1`.
- **Config via env** (`.env.edge` en `infra/env/`):
  - `FINCA_ID`, `ZONA_ID`, `DEVICE_ID` — defaults `finca1`, `zona1`, `pi-gw-001`
  - `MQTT_HOST`, `MQTT_PORT` — defaults `mosquitto`, `1883`
  - `INTERVAL_SEC`, `SENSORS` — defaults `60`, `10`
- **10 tipos de sensor** con rangos realistas: `soil_moisture`, `temperature`, `humidity`, `ec`, `ph`, `light`, `pressure`, `wind`, `rain`, `battery`.
- **Topic v2**: `agro/{FINCA}/{ZONA}/{DEVICE}/sensor/{sensorId}/{TYPE}/telemetry`.
- **LWT**: publica retain en `agro/.../{DEVICE}/status` con "online"/"offline".
- **Contenedor actual**: solo hace `time.sleep(10**9)` (placeholder). Para activar el simulador, cambiar command a `["python", "/app/simulator/main.py"]`.

## Edge — Mosquitto

- **Config**: `edge/mosquitto/config/mosquitto.conf` — anónimo, persistencia activa, log a archivo.
- **Overrides RPi**: `infra/compose/docker-compose.edge.rpi.yml` cambia volúmenes a `/opt/glea-nexo/edge-data/`.

## MQTT Topics (v2 — la que usa el código real)

```
agro/{finca}/{zona}/{gatewayUid}/sensor/{sensorUid}/{TYPE}/telemetry
agro/{finca}/{zona}/{gatewayUid}/sensor/{sensorUid}/status
agro/{finca}/{zona}/{gatewayUid}/actuator/{tipo}/cmd
agro/{finca}/{zona}/{gatewayUid}/actuator/{tipo}/state
agro/{finca}/{zona}/{gatewayUid}/alerts
```

**QoS**: telemetry=1, cmd=1, state=1+retain, alerts=1. Sin retain en telemetry.

## Composiciones

- `docker-compose.edge.yml` — mosquitto + nodered + edge-python (red: `glea-edge-net`)
- `docker-compose.platform.yml` — postgres + backend + frontend (red: `glea-platform-net`)
- `docker-compose.edge.rpi.yml` — override de volúmenes para RPi real
- **Siempre levantar ambos**: `-f edge.yml -f platform.yml up -d`

## Testing

```
# Backend — todos los tests
cd backend && mvn test

# Backend — solo unitarios (sin Docker)
mvn test -Dtest=TopicParserTest

# Backend — solo integración (requiere Docker)
mvn test -Dtest=*IntegrationTest

# Frontend (requiere Chrome)
cd frontend && ng test
```

Los tests de integración usan TestContainers PostgreSQL. El TopicParser tiene cobertura de topics v1 y v2.

## Convenciones de código notables

- **Sin comentarios en código** (estilo del proyecto). No añadir Javadoc ni comentarios inline.
- **Maven wrapper**: no hay `.mvn/` — usar `mvn` del sistema.
- **Angular**: standalone components, sin NgModules, sin routing library.
- **DTOs**: en `api/dto/` separados por dominio. Sin MapStruct — mapeo manual.
- **ID types**: `UUID` para entities de dominio (Farm, Zone, Device). `Long` para catálogos (SensorType, Unit).
- **Log correlationId**: `MDC.put("correlationId", ...)` via `CorrelationIdFilter`.

## Referencias que ya existen

- `README.md` — visión general, API list, testing, quickstart.
- `roadmap.md` — especificación funcional detallada y roadmap.
- `docs/api/inventory-openapi.md` — contratos CRUD inventario.
- `docs/runbook/inventory-crud.md` — runbook CRUD.
- `docs/iterations/` — ITER-001 a ITER-004 con estado actual.
- `infra/env/.env.edge` — defaults para edge services.
