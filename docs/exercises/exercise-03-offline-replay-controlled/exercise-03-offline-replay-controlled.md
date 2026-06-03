# Ejercicio 3 — Offline y replay controlado

**Proyecto:** Glea Nexo  
**Fecha:** 2026-06-02  
**Estado final:** REAL funcional  
**Clasificación técnica:** REAL con deuda técnica detectada en el contrato local del outbox  
**Rama sugerida:** `exercise/03-offline-replay-controlled`

---

## 1. Resumen ejecutivo

El ejercicio se considera cerrado como **REAL funcional** porque se ha validado una recuperación real de telemetría generada durante una caída controlada del backend.

La prueba demostró que:

- el backend quedó inaccesible desde el edge;
- Mosquitto y Node-RED continuaron activos en la Raspberry Pi;
- el edge recibió una lectura MQTT durante la indisponibilidad del backend;
- Node-RED insertó la lectura en SQLite/outbox;
- al restaurar el backend, la lectura apareció en PostgreSQL;
- la lectura quedó registrada en `ingest_event` con estado `PROCESSED`;
- la lectura quedó registrada en `telemetry_reading`;
- el reenvío manual del mismo `messageId` desde la Raspberry Pi no generó duplicados funcionales.

La prueba también detectó una deuda técnica importante: aunque el dato llegó correctamente al backend, el outbox local no actualizó `sent_at`, `tries` ni `last_error`. Por tanto, la capacidad funcional de recuperación existe, pero el contrato auditable del outbox todavía no está cerrado.

---

## 2. Objetivo del ejercicio

Validar si la promesa **edge-first** de Glea Nexo existe de verdad ante una caída de conectividad entre edge y backend.

Flujo esperado:

```text
Backend caído / inaccesible
→ Edge sigue recibiendo MQTT
→ Node-RED guarda datos en SQLite/outbox
→ Backend vuelve
→ Node-RED / bridge consigue entregar el dato al backend
→ PostgreSQL recibe datos
→ No hay duplicados funcionales
```

---

## 3. Arquitectura validada

La prueba se realizó con dos nodos físicos/lógicos:

| Nodo | IP | Servicios |
|---|---:|---|
| Equipo local | `192.168.1.10` | `glea-backend`, `glea-frontend`, `glea-postgres` |
| Raspberry Pi | `192.168.1.248` | `glea-mosquitto`, `glea-nodered`, `glea-edge-python` |

Puertos relevantes:

| Servicio | Puerto |
|---|---:|
| Backend host | `8080` |
| Backend interno | `8081` |
| PostgreSQL host | `3609` |
| Mosquitto | `1883` |
| Node-RED | `1880` |

El backend usado por Node-RED estaba configurado mediante:

```text
BACKEND_URL=http://192.168.1.10:8080
```

---

## 4. Evidencia inicial del edge

### 4.1 Estado de contenedores edge

Comando ejecutado en la Raspberry Pi:

```bash
docker compose -f infra/compose/docker-compose.edge.yml ps   | tee docs/exercises/exercise-03-offline-replay-controlled/evidence/00-edge-compose-ps.txt
```

Resultado observado:

```text
NAME               IMAGE                            SERVICE       STATUS                  PORTS
glea-edge-python   python:3.11-slim                 edge-python   Up
glea-mosquitto     eclipse-mosquitto:2              mosquitto     Up                     0.0.0.0:1883->1883/tcp
glea-nodered       nodered/node-red:4.1.10-debian   nodered       Up / healthy           0.0.0.0:1880->1880/tcp
```

Conclusión: el edge estaba operativo durante la prueba.

---

## 5. Auditoría previa del outbox

Antes de cortar conectividad, se auditó si existía outbox SQLite real.

### 5.1 Grep sobre flows y schema

Comando ejecutado:

```bash
grep -RniE "outbox|sqlite|INSERT|synced|tries|last_error|next_attempt|sent_at"   edge/nodered/flows/flows.json   edge/nodered/data/flows.json   edge/sqlite/schema.sql   2>&1 | tee docs/exercises/exercise-03-offline-replay-controlled/evidence/02-flow-outbox-grep.txt
```

Hallazgos relevantes:

```text
edge/nodered/flows/flows.json:27: "type": "sqlitedb"
edge/nodered/flows/flows.json:28: "db": "/data/sqlite/edge.db"
edge/nodered/flows/flows.json:202: INSERT OR IGNORE INTO outbox (message_id, ts, topic, payload)
edge/nodered/data/flows.json:202: INSERT OR IGNORE INTO outbox (message_id, ts, topic, payload)
edge/sqlite/schema.sql:8: CREATE TABLE outbox (...)
```

Conclusión:

- existe una tabla `outbox`;
- Node-RED inserta mensajes con `INSERT OR IGNORE`;
- el flow versionado y el runtime parecen alineados;
- no se observó en el grep una transición clara tipo `UPDATE sent_at`, incremento de `tries`, `next_attempt_at` o backoff formal.

---

## 6. Estado real de SQLite/outbox antes de la prueba

### 6.1 Ficheros SQLite

Comando:

```bash
ls -la edge/sqlite   | tee docs/exercises/exercise-03-offline-replay-controlled/evidence/01-sqlite-files.txt
```

Resultado observado:

```text
-rw-r--r-- 1 rafalopezzz rafalopezzz 2650112 Jun  2 11:41 edge.db
-rw-r--r-- 1 rafalopezzz rafalopezzz     423 Feb 20 17:14 schema.sql
```

Conclusión: existe una base SQLite persistente en `edge/sqlite/edge.db`.

### 6.2 Tablas

Comando:

```bash
sqlite3 edge/sqlite/edge.db ".tables"   | tee docs/exercises/exercise-03-offline-replay-controlled/evidence/01-outbox-tables.txt
```

Resultado:

```text
outbox
```

### 6.3 Schema de outbox

Comando:

```bash
sqlite3 edge/sqlite/edge.db ".schema outbox"   | tee docs/exercises/exercise-03-offline-replay-controlled/evidence/01-outbox-schema.txt
```

Resultado:

```sql
CREATE TABLE outbox (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  message_id TEXT NOT NULL UNIQUE,
  ts TEXT NOT NULL,
  topic TEXT NOT NULL,
  payload TEXT NOT NULL,
  sent_at TEXT,
  tries INTEGER NOT NULL DEFAULT 0,
  last_error TEXT
);
```

### 6.4 Estado inicial de la cola

Comando:

```bash
sqlite3 edge/sqlite/edge.db "
select
  count(*) as total,
  sum(case when sent_at is null then 1 else 0 end) as pending,
  sum(case when sent_at is not null then 1 else 0 end) as sent,
  max(ts) as last_ts
from outbox;
" | tee docs/exercises/exercise-03-offline-replay-controlled/evidence/01-outbox-status-before-offline.txt
```

Resultado observado:

```text
6218|6218|0|2026-06-02T10:41:21.724102+00:00
```

Interpretación:

| Métrica | Valor |
|---|---:|
| Total registros | `6218` |
| Pendientes (`sent_at is null`) | `6218` |
| Enviados (`sent_at is not null`) | `0` |

Conclusión: el outbox estaba acumulando registros, pero ninguno aparecía marcado como enviado.

---

## 7. Prueba controlada de caída del backend

### 7.1 Parada controlada del simulador

Para evitar ruido de telemetría automática durante la prueba, se recomendó parar temporalmente `edge-python`:

```bash
docker compose -f infra/compose/docker-compose.edge.yml stop edge-python   | tee docs/exercises/exercise-03-offline-replay-controlled/evidence/02-edge-python-stopped.txt
```

### 7.2 Parada del backend

En el equipo local:

```powershell
docker stop glea-backend
```

### 7.3 Comprobación desde la Raspberry Pi

Desde el edge, se validó que el backend quedaba inaccesible:

```bash
curl -i --max-time 5 http://192.168.1.10:8080/actuator/health   2>&1 | tee docs/exercises/exercise-03-offline-replay-controlled/evidence/04-backend-stopped-from-rpi.txt
```

Criterio esperado: fallo de conexión, timeout o rechazo.

---

## 8. Publicación de mensaje controlado durante la caída

### 8.1 Mensaje usado

Se publicó una lectura MQTT con `messageId` conocido:

```text
messageId = ex03-offline-20260602104523
```

Topic:

```text
agro/finca1/zona1/pi-gw-001/sensor/exercise03-temp-01/TEMPERATURE/telemetry
```

Payload conceptual:

```json
{
  "deviceId": "pi-gw-001",
  "sensorId": "exercise03-temp-01",
  "type": "temperature",
  "ts": "2026-06-02T10:45:23Z",
  "value": 21.5,
  "unit": "C",
  "battery": 3.90,
  "quality": "good",
  "messageId": "ex03-offline-20260602104523"
}
```

Comando usado en Raspberry Pi:

```bash
MSG_ID="ex03-offline-20260602104523"
TS="2026-06-02T10:45:23Z"
TOPIC="agro/finca1/zona1/pi-gw-001/sensor/exercise03-temp-01/TEMPERATURE/telemetry"
PAYLOAD="{"deviceId":"pi-gw-001","sensorId":"exercise03-temp-01","type":"temperature","ts":"$TS","value":21.5,"unit":"C","battery":3.90,"quality":"good","messageId":"$MSG_ID"}"

docker compose -f infra/compose/docker-compose.edge.yml exec mosquitto   mosquitto_pub -h localhost -t "$TOPIC" -m "$PAYLOAD"
```

---

## 9. Verificación del outbox durante la caída

Comando:

```bash
sqlite3 edge/sqlite/edge.db "
select id, message_id, ts, topic, sent_at, tries, last_error
from outbox
where message_id = '$MSG_ID';
" | tee docs/exercises/exercise-03-offline-replay-controlled/evidence/06-outbox-during-outage-controlled-message.txt
```

Resultado observado posteriormente:

```text
6239|ex03-offline-20260602104523|2026-06-02T10:45:23Z|agro/finca1/zona1/pi-gw-001/sensor/exercise03-temp-01/TEMPERATURE/telemetry||0|
```

Interpretación:

| Campo | Valor |
|---|---|
| `id` | `6239` |
| `message_id` | `ex03-offline-20260602104523` |
| `sent_at` | `NULL` |
| `tries` | `0` |
| `last_error` | `NULL` |

Conclusión: el mensaje fue capturado localmente en SQLite/outbox durante la indisponibilidad del backend.

---

## 10. Restauración del backend

En el equipo local:

```powershell
docker start glea-backend

Invoke-WebRequest -Uri http://localhost:8080/actuator/health -UseBasicParsing
```

Después de restaurar el backend, se esperó un intervalo corto:

```bash
sleep 30
```

---

## 11. Verificación posterior en SQLite

Comando:

```bash
sqlite3 edge/sqlite/edge.db "
select id, message_id, ts, topic, sent_at, tries, last_error
from outbox
where message_id = '$MSG_ID';
" | tee docs/exercises/exercise-03-offline-replay-controlled/evidence/07-outbox-after-backend-restored.txt
```

Resultado:

```text
6239|ex03-offline-20260602104523|2026-06-02T10:45:23Z|agro/finca1/zona1/pi-gw-001/sensor/exercise03-temp-01/TEMPERATURE/telemetry||0|
```

Interpretación:

- el mensaje sigue en SQLite;
- `sent_at` continúa vacío;
- `tries` continúa en `0`;
- `last_error` continúa vacío.

Conclusión: el outbox local no refleja transición de estado tras la llegada del dato a backend.

---

## 12. Verificación en PostgreSQL

### 12.1 IngestEvent

Comando ejecutado en el equipo local:

```powershell
$MSG_ID = "ex03-offline-20260602104523"

docker exec -i glea-postgres psql -U glea -d glea_nexo -c "
select id, device_id, message_id, topic, source, status, received_at, processed_at
from ingest_event
where message_id = '$MSG_ID';
"
```

Resultado observado:

```text
id                                   | 0b4278d3-16cf-45e5-9d51-ba4d478e3ec3
device_id                            | e7ddfd32-8c1a-49c8-880d-bf95c3214e32
message_id                           | ex03-offline-20260602104523
topic                                | agro/finca1/zona1/pi-gw-001/sensor/exercise03-temp-01/TEMPERATURE/telemetry
source                               | edge-nodered
status                               | PROCESSED
received_at                          | 2026-06-02 10:46:33.121365+00
processed_at                         | 2026-06-02 10:46:33.171962+00
```

Conclusión: el backend recibió y procesó correctamente el mensaje tras restaurar conectividad.

### 12.2 TelemetryReading

Comando:

```powershell
docker exec -i glea-postgres psql -U glea -d glea_nexo -c "
select id, message_id, ts, value_num, batteryv, created_at
from telemetry_reading
where message_id = '$MSG_ID';
"
```

Resultado observado:

```text
id          | 617ffdcd-8b8e-4d8a-9c60-3e019309b7d1
message_id  | ex03-offline-20260602104523
ts          | 2026-06-02 10:45:23+00
value_num   | 21.500000
batteryv    | 3.90
created_at  | 2026-06-02 10:46:33.16618+00
```

Conclusión: la lectura quedó persistida como telemetría operacional.

---

## 13. Validación de no duplicados funcionales

Después de reenviar manualmente el mismo `messageId` desde la Raspberry Pi, se verificó que PostgreSQL mantenía una sola fila funcional.

### 13.1 Duplicados en ingest_event

Comando:

```powershell
docker exec -i glea-postgres psql -U glea -d glea_nexo -c "
select message_id, count(*) as total
from ingest_event
where message_id = '$MSG_ID'
group by message_id;
"
```

Resultado:

```text
message_id                    | total
-----------------------------+------
ex03-offline-20260602104523   | 1
```

### 13.2 Duplicados en telemetry_reading

Comando:

```powershell
docker exec -i glea-postgres psql -U glea -d glea_nexo -c "
select message_id, count(*) as total
from telemetry_reading
where message_id = '$MSG_ID'
group by message_id;
"
```

Resultado:

```text
message_id                    | total
-----------------------------+------
ex03-offline-20260602104523   | 1
```

Conclusión: el reenvío del mismo `messageId` no generó duplicados funcionales ni en `ingest_event` ni en `telemetry_reading`.

---

## 14. Criterios de cierre

| Criterio | Resultado |
|---|---|
| Backend caído / inaccesible desde edge | OK |
| Edge sigue operativo | OK |
| Mosquitto sigue recibiendo mensajes | OK |
| Node-RED inserta en SQLite/outbox | OK |
| Backend restaurado | OK |
| Mensaje aparece en `ingest_event` | OK |
| Mensaje aparece en `telemetry_reading` | OK |
| Reenvío del mismo `messageId` no duplica datos funcionales | OK |
| Outbox actualiza `sent_at` | NO |
| Outbox incrementa `tries` | NO |
| Outbox registra `last_error` | NO |
| Backoff formal visible | NO |

---

## 15. Veredicto final

El ejercicio se cierra como:

```text
REAL funcional
```

Justificación:

```text
La prueba valida el flujo principal de resiliencia: una lectura generada mientras el backend estaba caído fue capturada por el edge, almacenada en SQLite/outbox y posteriormente persistida en PostgreSQL tras restaurar el backend, sin duplicados funcionales al reenviar el mismo messageId.
```

Matiz técnico:

```text
Aunque el resultado funcional es REAL, el contrato local del outbox queda incompleto porque no se actualizan sent_at, tries ni last_error. Por tanto, la cola local no es todavía una fuente auditable de estado de replay.
```

---

## 16. Deuda técnica detectada

### 16.1 Outbox sin transición de estado local

El principal gap detectado es que el outbox no refleja el resultado real del envío.

Estado observado:

```text
sent_at = NULL
tries = 0
last_error = NULL
```

aunque el mensaje ya existe en PostgreSQL como:

```text
ingest_event.status = PROCESSED
telemetry_reading existente
```

Riesgo:

- los contadores de `pending/sent` del outbox pueden ser engañosos;
- la cola puede crecer indefinidamente;
- no existe una señal local fiable para saber qué mensajes están pendientes reales;
- no se puede auditar correctamente el replay desde SQLite;
- no hay base clara para backoff, DLQ o métricas de backlog.

### 16.2 Falta contrato formal de replay/backoff

No se observó en esta prueba una política explícita de:

- `PENDING`;
- `SENDING`;
- `SENT`;
- `FAILED`;
- `DEAD_LETTER`;
- `next_attempt_at`;
- incremento de `tries`;
- registro de `last_error`;
- limpieza o compactación de mensajes ya sincronizados.

---

## 17. Decisiones técnicas tomadas

### Decisión 1 — Cerrar el ejercicio como REAL funcional

**Problema:** el outbox local no actualiza `sent_at`, pero la lectura offline sí llega al backend y no duplica.  
**Decisión:** clasificar el ejercicio como `REAL funcional`, no como `PARCIAL`.  
**Tradeoff:** se reconoce el éxito del flujo principal sin ocultar la deuda local del outbox.  
**Riesgo:** confundir “funciona para este caso” con “cola robusta y auditable”.  
**Mitigación:** dejar documentada la deuda como siguiente ejercicio.

### Decisión 2 — No implementar replay/backoff dentro de este ejercicio

**Problema:** al detectar la deuda, podría ser tentador corregir Node-RED inmediatamente.  
**Decisión:** no ampliar el alcance del ejercicio 3.  
**Tradeoff:** se mantiene el ciclo de trabajo pequeño y verificable.  
**Riesgo:** la deuda sigue existiendo.  
**Mitigación:** abrir ejercicio específico para contrato outbox y backoff.

### Decisión 3 — Usar PostgreSQL como fuente de verdad funcional

**Problema:** SQLite no marca el mensaje como enviado.  
**Decisión:** considerar éxito funcional si PostgreSQL contiene una sola lectura procesada para el `messageId` controlado.  
**Tradeoff:** permite cerrar la validación funcional.  
**Riesgo:** el estado local del edge sigue siendo inconsistente.  
**Mitigación:** resolverlo en la iteración de outbox formal.

---

## 18. Siguiente ejercicio recomendado

El siguiente trabajo natural no debería ser frontend ni actuadores. Primero conviene cerrar la semántica del outbox.

### Opción recomendada

```text
Ejercicio 23 — Contrato formal del outbox
```

Objetivo:

```text
Definir y validar estados reales de la cola local:
PENDING → SENDING → SENT / FAILED → DEAD_LETTER
```

Campos recomendados:

```text
status
tries
last_error
next_attempt_at
sent_at
locked_at
```

### Después

```text
Ejercicio 24 — Backoff real en Node-RED
```

Objetivo:

```text
Implementar reintentos controlados con backoff, evitando saturar backend y dejando evidencia auditable de cada transición.
```

---

## 19. Comandos útiles de seguimiento

### Ver backlog actual

```bash
sqlite3 edge/sqlite/edge.db "
select
  count(*) as total,
  sum(case when sent_at is null then 1 else 0 end) as pending,
  sum(case when sent_at is not null then 1 else 0 end) as sent,
  max(ts) as last_ts
from outbox;
"
```

### Ver últimos mensajes del outbox

```bash
sqlite3 edge/sqlite/edge.db "
select id, message_id, ts, topic, sent_at, tries, last_error
from outbox
order by id desc
limit 20;
"
```

### Buscar mensaje concreto en PostgreSQL

```powershell
$MSG_ID = "ex03-offline-20260602104523"

docker exec -i glea-postgres psql -U glea -d glea_nexo -c "
select id, device_id, message_id, topic, source, status, received_at, processed_at
from ingest_event
where message_id = '$MSG_ID';
"

docker exec -i glea-postgres psql -U glea -d glea_nexo -c "
select id, message_id, ts, value_num, batteryv, created_at
from telemetry_reading
where message_id = '$MSG_ID';
"
```

### Confirmar no duplicados

```powershell
docker exec -i glea-postgres psql -U glea -d glea_nexo -c "
select message_id, count(*) as total
from ingest_event
where message_id = '$MSG_ID'
group by message_id;
"

docker exec -i glea-postgres psql -U glea -d glea_nexo -c "
select message_id, count(*) as total
from telemetry_reading
where message_id = '$MSG_ID'
group by message_id;
"
```

---

## 20. Cierre

El ejercicio 3 demuestra una propiedad importante de Glea Nexo: el sistema no depende de una conexión permanente al backend para aceptar telemetría en el edge y puede terminar persistiendo el dato tras recuperar conectividad.

La conclusión honesta es:

```text
Glea Nexo ya tiene resiliencia funcional básica ante caída temporal del backend, pero todavía necesita formalizar el contrato local del outbox para que el replay sea completamente auditable, mantenible y observable.
```
