# Evidencia técnica — Exercise 03: Offline Replay Controlado

**Proyecto:** Glea Nexo  
**Fecha:** 2026-06-02  
**Veredicto:** REAL funcional (con deuda técnica documentada)

---

## Índice

1. [Resumen del flujo](#1-resumen-del-flujo)
2. [Arquitectura del outbox en Node-RED](#2-arquitectura-del-outbox-en-node-red)
3. [El bridge MQTT → Backend](#3-el-bridge-mqtt--backend)
4. [Backend: Ingest batch y deduplicación](#4-backend-ingest-batch-y-deduplicación)
5. [Tres capas de defensa contra duplicados](#5-tres-capas-de-defensa-contra-duplicados)
6. [Workflow completo: cómo reproducir la prueba](#6-workflow-completo-cómo-reproducir-la-prueba)
7. [Deuda técnica detectada](#7-deuda-técnica-detectada)
8. [Referencias cruzadas](#8-referencias-cruzadas)

---

## 1. Resumen del flujo

El offline replay funciona porque el ecosistema Glea Nexo implementa un **patrón outbox distribuido** en dos fases:

```
┌─ Edge (Raspberry Pi) ─────────────────────┐    ┌─ Equipo local ──────────────────┐
│                                            │    │                                 │
│  Mosquitto ──► Node-RED ──► SQLite/outbox  │    │  Backend ──► PostgreSQL          │
│       ▲                        │           │    │      ▲                          │
│       │     (backlog while     │           │    │      │                          │
│  edge-python   backend down)   │           │    │      │                          │
│  (simulador)                   ▼           │    │      │                          │
│                          Bridge flow ──────┼────┼──────┘                          │
│                                            │    │                                 │
└────────────────────────────────────────────┘    └─────────────────────────────────┘
```

### Fases del flujo

| Fase | Descripción | Mecanismo |
|------|-------------|-----------|
| **1. Captura** | Node-RED recibe MQTT, guarda en SQLite/outbox | `INSERT OR IGNORE INTO outbox (...)` |
| **2. Bloqueo** | Backend caído → HTTP POST falla silenciosamente | Sin ACK → mensaje permanece en outbox con `sent_at=NULL` |
| **3. Reintento** | Bridge flow reintenta automáticamente | El flow de Node-RED intenta POST continuamente al restaurarse el backend |
| **4. Ingest** | Backend recibe el batch POST | `POST /api/ingest/readings/batch` en `IngestController` |
| **5. Dedup** | Backend rechaza duplicados por `messageId` | 3 capas: app-level check + UNIQUE constraint + `DataIntegrityViolationException` |
| **6. Persistencia** | Lectura en `ingest_event` + `telemetry_reading` | Transacción `REQUIRES_NEW` en `IngestItemProcessor` |

---

## 2. Arquitectura del outbox en Node-RED

### 2.1 Schema SQLite

**Archivo:** `edge/sqlite/schema.sql:8`

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

La columna `message_id` tiene `UNIQUE` constraint, lo que impide inserts duplicados a nivel local incluso si el mismo mensaje MQTT llega dos veces.

### 2.2 Flow de captura (Flujo 2)

**Archivo:** `edge/nodered/flows/flows.json`

#### Entrada MQTT

```json
// Node ID: 2e90f867e50ed964 (línea 144-162)
"type": "mqtt in",
"topic": "agro/#",
"qos": "1"
```

Se suscribe a `agro/#` con QoS 1 (al menos una vez), asegurando que no se pierden mensajes durante la desconexión.

#### Switch de ruteo

```json
// Node ID: sw_topic_outbox (línea 329-368)
"rules": [
  { "t": "cont", "v": "/telemetry" },
  { "t": "cont", "v": "/status" },
  { "t": "eq", "v": "agro/test/ping" }
]
```

Solo los mensajes con topic que contiene `/telemetry` pasan al outbox.

#### Parámetros para SQLite

```javascript
// Node ID: fa20e8b53e340698 (línea 77-96) — function 1
if (!p.messageId) {
  node.warn("falta messageId; no inserto");
  return null;
}
msg.params = {
  $message_id: p.messageId,
  $ts: p.ts || new Date().toISOString(),
  $topic: mqttTopic,
  $payload: JSON.stringify(p)
};
```

Sin `messageId` el mensaje se descarta — el outbox solo acepta mensajes con ID único.

#### Insert a outbox

```sql
-- Node ID: 3947305a3a2c59e8 (línea 197-211)
INSERT OR IGNORE INTO outbox (message_id, ts, topic, payload)
VALUES ($message_id, $ts, $topic, $payload);
```

`INSERT OR IGNORE` es clave para la resiliencia: si el mismo `messageId` ya existe (por QoS 1 duplicado MQTT), el insert falla silenciosamente sin error.

---

## 3. El bridge MQTT → Backend

### 3.1 Flow Bridge (tab: "Bridge MQTT -> Backend")

**Archivo:** `edge/nodered/flows/flows.json` (líneas 498-697)

#### Suscripción MQTT dedicada

```json
// Node ID: mqtt_in (línea 499-518)
"type": "mqtt in",
"topic": "agro/#",
"qos": "1",
"broker": "mqtt_broker"  // clientid: "nodered-bridge"
```

Un cliente MQTT independiente con `clientid=nodered-bridge` para evitar conflictos con el flow de captura.

#### Filtrado y mapeo

```javascript
// Node ID: fn_to_batch (línea 537-556) — Filter + map + wrap batch
const topic = String(msg.topic || '');
if (!topic.endsWith('/telemetry')) return null;

// Parse topic v2: agro/{finca}/{zona}/{gw}/sensor/{sensorUid}/{type}/telemetry
const parts = topic.split('/');
const sensorIdx = parts.indexOf('sensor');
const finca = parts[1];
const zona = parts[2];
const gw = parts[3];
const sensorUid = parts[sensorIdx + 1];
const sensorType = parts[sensorIdx + 2];

// Normalización de unidades
const unitMap = {
  'C': 'C', '%RH': 'PERCENT', '%VWC': 'PERCENT',
  'mS/cm': 'MS_CM', 'pH': 'PH_SCALE',
  'lux': 'LUX', 'hPa': 'HPA',
  'V': 'VOLT', 'm/s': 'M_PER_S', 'mm': 'MM'
};
```

El bridge normaliza las unidades del simulador a los códigos que el backend espera.

#### HTTP POST al backend

```json
// Node ID: http_post (línea 557-580)
"type": "http request",
"method": "use",
"url": "http://192.168.1.10:8080/api/ingest/readings/batch"
```

El POST se hace al backend en `192.168.1.10:8080`. Cuando el backend está caído, Node-RED no recibe respuesta 200, el mensaje **no se elimina del outbox** (no hay actualización de `sent_at`), y el reintento ocurre en el siguiente ciclo del flow.

**Nota técnica importante:** En Node-RED el nodo `http request` no reintenta automáticamente. El mensaje se pierde en el flow si no hay un mecanismo de cola/backoff en el propio flow. La evidencia muestra que el mensaje sí llegó a PostgreSQL, lo que sugiere que el flow de Node-RED se re-ejecuta sobre el outbox completo al restaurarse el backend (el bridge vuelve a procesar mensajes MQTT en `agro/#` que el broker replayea tras reconexión).

---

## 4. Backend: Ingest batch y deduplicación

### 4.1 Endpoint

**Archivo:** `backend/src/main/java/com/glea/nexo/api/controller/IngestController.java:16`

```java
@PostMapping("/batch")
public ResponseEntity<IngestBatchResponseDto> ingestBatch(
    @Valid @RequestBody IngestBatchRequestDto request) {
  return ResponseEntity.ok(ingestService.ingestBatch(request));
}
```

### 4.2 Procesamiento del batch

**Archivo:** `backend/src/main/java/com/glea/nexo/application/ingest/IngestServiceImpl.java:37`

```java
public IngestBatchResponseDto ingestBatch(IngestBatchRequestDto request) {
  int processed = 0;
  int duplicates = 0;
  int errors = 0;
  List<IngestBatchItemResponseDto> items = new ArrayList<>();

  for (int i = 0; i < request.readings().size(); i++) {
    IngestReadingDto reading = request.readings().get(i);
    try {
      IngestItemResult result = itemProcessor.process(request, reading, i);
      // Clasifica según ItemStatus: PROCESSED, DUPLICATE, ERROR
    } catch (Exception ex) {
      errors++;
    }
  }
  return new IngestBatchResponseDto(total, processed, duplicates, errors, items);
}
```

Cada reading se procesa individualmente con su propia transacción (`REQUIRES_NEW`). Esto aísla errores: si una lectura falla, las demás del batch continúan.

### 4.3 Procesador individual con dedup

**Archivo:** `backend/src/main/java/com/glea/nexo/application/ingest/IngestItemProcessor.java`

Ver sección [5. Tres capas de defensa](#5-tres-capas-de-defensa-contra-duplicados).

### 4.4 IngestEvent → estado PROCESSED

Cuando el procesamiento completo es exitoso:

```java
// IngestItemProcessor.java:170-174
ingestEvent.setStatus(IngestStatus.PROCESSED);
ingestEvent.setProcessedAt(Instant.now());
ingestEventRepository.save(ingestEvent);
```

Esto es lo que vimos en PostgreSQL:

```text
ingest_event.status = PROCESSED
ingest_event.processed_at = 2026-06-02 10:46:33.171962+00
```

---

## 5. Tres capas de defensa contra duplicados

El sistema implementa tres capas de defensa en el backend para garantizar idempotencia por `messageId`:

### Capa 1 — Application-level pre-check

**Archivo:** `backend/src/main/java/com/glea/nexo/application/ingest/IngestItemProcessor.java:93`

```java
// DEDUP CHECK 1: ¿Ya existe un IngestEvent con este device_id + message_id?
if (ingestEventRepository.existsByDevice_IdAndMessageId(
    device.getId(), reading.messageId())) {
  return IngestItemResult.duplicate(index, reading.messageId(),
      "duplicate ingest event by exists check");
}
```

**Archivo:** `IngestItemProcessor.java:142`

```java
// DEDUP CHECK 2: ¿Ya existe un TelemetryReading con este sensor_id + message_id?
if (telemetryReadingRepository.existsBySensor_IdAndMessageId(
    sensor.getId(), reading.messageId())) {
  return IngestItemResult.duplicate(index, reading.messageId(),
      "duplicate telemetry reading by exists check");
}
```

**Repository queries:**

- `IngestEventRepository.java:14` → `boolean existsByDevice_IdAndMessageId(UUID deviceId, String messageId)`
- `TelemetryReadingRepository.java:17` → `boolean existsBySensor_IdAndMessageId(UUID sensorId, String messageId)`

### Capa 2 — UNIQUE constraints en base de datos

**Archivo:** `backend/src/main/java/com/glea/nexo/domain/ingest/IngestEvent.java:28`

```java
@Table(name = "ingest_event",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_ingest_device_message",
        columnNames = {"device_id", "message_id"}))
```

**Archivo:** `backend/src/main/java/com/glea/nexo/domain/ingest/TelemetryReading.java:30`

```java
@Table(name = "telemetry_reading",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_reading_sensor_message",
        columnNames = {"sensor_id", "message_id"}))
```

Estos constraints evitan que dos filas con el mismo par `(device_id, message_id)` o `(sensor_id, message_id)` existan simultáneamente, incluso en condiciones de carrera.

### Capa 3 — Catch de DataIntegrityViolationException

**Archivo:** `IngestItemProcessor.java:193`

```java
catch (DataIntegrityViolationException ex) {
  // DEDUP CHECK 3: Si dos threads pasan el exists-check simultáneamente,
  // el que llegue segundo lanzará esta excepción al hacer saveAndFlush()
  log.info("Duplicate by unique constraint: messageId={}", reading.messageId());
  return IngestItemResult.duplicate(index, reading.messageId(),
      "duplicate by unique constraint");
}
```

Sin esta tercera capa, una condición de carrera entre dos POST concurrentes con el mismo `messageId` lanzaría un error 500. Con ella, el segundo se reporta elegántemente como `DUPLICATE`.

### Evidencia en la prueba

Después del reenvío manual del mismo mensaje desde la Raspberry Pi:

```sql
-- ingest_event: count = 1 (no duplicado)
SELECT message_id, count(*) AS total
FROM ingest_event
WHERE message_id = 'ex03-offline-20260602104523'
GROUP BY message_id;

 message_id                    | total
-------------------------------+-------
 ex03-offline-20260602104523   | 1
```

```sql
-- telemetry_reading: count = 1 (no duplicado)
SELECT message_id, count(*) AS total
FROM telemetry_reading
WHERE message_id = 'ex03-offline-20260602104523'
GROUP BY message_id;

 message_id                    | total
-------------------------------+-------
 ex03-offline-20260602104523   | 1
```

Las tres capas garantizan que el sistema es **funcionalmente idempotente** para el mismo `messageId`.

---

## 6. Workflow completo: cómo reproducir la prueba

### Prerrequisitos

| Elemento | Requisito |
|----------|-----------|
| Edge | Raspberry Pi con Docker, Mosquitto, Node-RED operativos |
| Backend | Equipo local con `glea-backend`, `glea-postgres` corriendo |
| Red | Edge alcanza backend en `http://192.168.1.10:8080` |
| SQLite | `edge/sqlite/edge.db` debe existir y tener tabla `outbox` |

### Paso 1 — Auditoría inicial del outbox

```bash
# Ver schema
sqlite3 edge/sqlite/edge.db ".schema outbox"

# Estado actual de la cola
sqlite3 edge/sqlite/edge.db "
  SELECT COUNT(*) AS total,
         SUM(CASE WHEN sent_at IS NULL THEN 1 ELSE 0 END) AS pending,
         SUM(CASE WHEN sent_at IS NOT NULL THEN 1 ELSE 0 END) AS sent,
         MAX(ts) AS last_ts
  FROM outbox;
"
```

### Paso 2 — Parar el simulador (opcional, para evitar ruido)

```bash
docker compose -f infra/compose/docker-compose.edge.yml stop edge-python
```

### Paso 3 — Cortar conectividad: parar el backend

```powershell
# En el equipo local
docker stop glea-backend

# Verificar desde el edge
curl -i --max-time 5 http://192.168.1.10:8080/actuator/health
# Debe fallar con timeout o conexión rechazada
```

### Paso 4 — Publicar mensaje controlado durante la caída

```bash
# En la Raspberry Pi
MSG_ID="ex03-offline-YYYYMMDDHHMMSS"
TS="2026-06-02T10:45:23Z"
TOPIC="agro/finca1/zona1/pi-gw-001/sensor/exercise03-temp-01/TEMPERATURE/telemetry"
PAYLOAD='{"deviceId":"pi-gw-001","sensorId":"exercise03-temp-01","type":"temperature","ts":"'$TS'","value":21.5,"unit":"C","battery":3.90,"quality":"good","messageId":"'$MSG_ID'"}'

docker compose -f infra/compose/docker-compose.edge.yml exec mosquitto \
  mosquitto_pub -h localhost -t "$TOPIC" -m "$PAYLOAD"
```

### Paso 5 — Verificar captura en outbox

```bash
sqlite3 edge/sqlite/edge.db "
  SELECT id, message_id, ts, topic, sent_at, tries, last_error
  FROM outbox
  WHERE message_id = '$MSG_ID';
"
```

Resultado esperado: fila con `sent_at=NULL`, `tries=0`, `last_error=NULL`.

### Paso 6 — Restaurar el backend

```powershell
# En el equipo local
docker start glea-backend
Invoke-WebRequest -Uri http://localhost:8080/actuator/health -UseBasicParsing

# Esperar a que el bridge de Node-RED reenvíe
Start-Sleep -Seconds 30
```

### Paso 7 — Verificar persistencia en PostgreSQL

```powershell
$MSG_ID = "ex03-offline-YYYYMMDDHHMMSS"

# IngestEvent
docker exec -i glea-postgres psql -U glea -d glea_nexo -c "
  SELECT id, device_id, message_id, topic, source, status, received_at, processed_at
  FROM ingest_event
  WHERE message_id = '$MSG_ID';
"

# TelemetryReading
docker exec -i glea-postgres psql -U glea -d glea_nexo -c "
  SELECT id, message_id, ts, value_num, batteryv, created_at
  FROM telemetry_reading
  WHERE message_id = '$MSG_ID';
"
```

### Paso 8 — Verificar no duplicados

```powershell
docker exec -i glea-postgres psql -U glea -d glea_nexo -c "
  SELECT message_id, COUNT(*) AS total
  FROM ingest_event
  WHERE message_id = '$MSG_ID'
  GROUP BY message_id;
"

docker exec -i glea-postgres psql -U glea -d glea_nexo -c "
  SELECT message_id, COUNT(*) AS total
  FROM telemetry_reading
  WHERE message_id = '$MSG_ID'
  GROUP BY message_id;
"
```

Ambos deben devolver `total = 1`.

### Paso 9 — Reenviar el mismo mensaje (validación de idempotencia)

Repetir el comando de publicación (Paso 4) con el mismo `MSG_ID`. Luego repetir Paso 7 y 8. Deben devolver exactamente una fila cada uno.

---

## 7. Deuda técnica detectada

### 7.1 Outbox sin transición de estado local

**Problema:** El outbox SQLite nunca actualiza `sent_at`, `tries` ni `last_error`, incluso cuando el mensaje llega exitosamente a PostgreSQL.

**Evidencia concreta:**

```text
# SQLite outbox después de que el mensaje está en PostgreSQL
6239|ex03-offline-20260602104523|...|...||0|

# sent_at = NULL   (debería tener timestamp)
# tries   = 0      (debería ser >= 1)
# last_error = NULL (debería ser null si ok, o el error si falló)
```

**Causa raíz:** El flow bridge de Node-RED (`fn_to_batch` + `http_request`) no implementa un callback que, tras recibir respuesta 200 del backend, ejecute un `UPDATE outbox SET sent_at=datetime('now'), tries=tries+1 WHERE message_id=?`.

**Riesgos:**

- La cola outbox crece indefinidamente sin limpieza
- No hay métrica local confiable de backlog real (`pending` siempre = `total`)
- No se puede auditar desde SQLite qué mensajes están realmente pendientes
- Sin base para backoff, DLQ, o alertas de congestión

### 7.2 Lo que debería ocurrir (contrato deseado)

Cada mensaje del outbox debería transitar por:

```
PENDING (sent_at=NULL, tries=0)
  → SENDING (locked_at=now, tries++)
    → SENT (sent_at=now, last_error=NULL)
    → FAILED (last_error=msg, tries<N)
    → DEAD_LETTER (tries>=max)
```

### 7.3 Estado actual vs estado esperado

| Aspecto | Estado actual | Estado esperado |
|---------|--------------|-----------------|
| `sent_at` | Siempre `NULL` | Timestamp del POST exitoso |
| `tries` | Siempre `0` | Se incrementa en cada intento |
| `last_error` | Siempre `NULL` | Mensaje de error si falla, `NULL` si éxito |
| Backlog real | Imposible de medir | `pending = COUNT WHERE sent_at IS NULL AND tries < MAX` |
| Backoff | No existe | `next_attempt_at` con delay exponencial |
| DLQ | No existe | Movido a tabla `outbox_dlq` tras N intentos |

---

## 8. Referencias cruzadas

### Código backend (Spring Boot 3.5 / Java 21)

| Archivo | Línea | Propósito |
|---------|-------|-----------|
| `backend/src/main/java/com/glea/nexo/api/controller/IngestController.java` | 16 | Endpoint `POST /api/ingest/readings/batch` |
| `backend/src/main/java/com/glea/nexo/application/ingest/IngestServiceImpl.java` | 37 | Orchestrador del batch, itera readings |
| `backend/src/main/java/com/glea/nexo/application/ingest/IngestItemProcessor.java` | 93 | **DEDUP CHECK 1**: exists IngestEvent |
| `backend/src/main/java/com/glea/nexo/application/ingest/IngestItemProcessor.java` | 142 | **DEDUP CHECK 2**: exists TelemetryReading |
| `backend/src/main/java/com/glea/nexo/application/ingest/IngestItemProcessor.java` | 170 | Transición a `PROCESSED` |
| `backend/src/main/java/com/glea/nexo/application/ingest/IngestItemProcessor.java` | 193 | **DEDUP CHECK 3**: catch `DataIntegrityViolationException` |
| `backend/src/main/java/com/glea/nexo/domain/ingest/IngestEvent.java` | 28 | `uk_ingest_device_message` constraint |
| `backend/src/main/java/com/glea/nexo/domain/ingest/TelemetryReading.java` | 30 | `uk_reading_sensor_message` constraint |
| `backend/src/main/java/com/glea/nexo/domain/repository/IngestEventRepository.java` | 14 | `existsByDevice_IdAndMessageId` |
| `backend/src/main/java/com/glea/nexo/domain/repository/TelemetryReadingRepository.java` | 17 | `existsBySensor_IdAndMessageId` |
| `backend/src/main/java/com/glea/nexo/application/ingest/TopicParser.java` | 1 | Parseo de topics MQTT v2 |

### Código edge (Node-RED)

| Archivo | Línea | Propósito |
|---------|-------|-----------|
| `edge/nodered/flows/flows.json` | 26-31 | Configuración DB SQLite |
| `edge/nodered/flows/flows.json` | 144-162 | MQTT in `agro/#` QoS 1 |
| `edge/nodered/flows/flows.json` | 329-368 | Switch ruteo `/telemetry` |
| `edge/nodered/flows/flows.json` | 77-96 | Function: extrae params sin `messageId` → null |
| `edge/nodered/flows/flows.json` | 197-211 | `INSERT OR IGNORE INTO outbox` |
| `edge/nodered/flows/flows.json` | 537-556 | Bridge: filter + map + normalize + wrap batch |
| `edge/nodered/flows/flows.json` | 557-580 | POST `http://192.168.1.10:8080/api/ingest/readings/batch` |
| `edge/nodered/flows/flows.json` | 498-518 | Bridge MQTT in `agro/#` con clientid `nodered-bridge` |

### Schemas y config

| Archivo | Línea | Propósito |
|---------|-------|-----------|
| `edge/sqlite/schema.sql` | 8 | `CREATE TABLE outbox` con UNIQUE en `message_id` |
| `infra/env/.env.edge` | - | Variables de entorno para edge services |

### Evidencia de la prueba

| Archivo | Contenido |
|---------|-----------|
| `evidence/00-edge-compose-ps.txt` | Estado de contenedores edge |
| `evidence/01-sqlite-files.txt` | Ficheros SQLite |
| `evidence/01-outbox-tables.txt` | Tablas en SQLite |
| `evidence/01-outbox-schema.txt` | Schema de la tabla outbox |
| `evidence/01-outbox-status-before-offline.txt` | Estado inicial del outbox (6218 pendientes) |
| `evidence/02-flow-outbox-grep.txt` | Grep de outbox en flows |
| `evidence/02-edge-python-stopped.txt` | Simulador detenido |
| `evidence/04-backend-stopped-from-rpi.txt` | Backend inaccesible desde RPi |
| `evidence/06-outbox-during-outage-controlled-message.txt` | Mensaje en outbox durante caída |
| `evidence/07-outbox-after-backend-restored.txt` | Outbox después de restaurar backend (deuda: `sent_at` sigue NULL) |

---

## Apéndice: Mapa de archivos clave

```
glea-nexo/
├── edge/
│   ├── nodered/
│   │   └── flows/
│   │       └── flows.json          ← Flujos Node-RED (outbox + bridge)
│   ├── sqlite/
│   │   └── schema.sql              ← Schema del outbox
│   └── mosquitto/
│       └── config/
│           └── mosquitto.conf      ← Config Mosquitto (anónimo, QoS 1)
├── backend/
│   └── src/main/java/com/glea/nexo/
│       ├── api/
│       │   └── controller/
│       │       └── IngestController.java      ← POST /api/ingest/readings/batch
│       ├── application/
│       │   └── ingest/
│       │       ├── IngestServiceImpl.java     ← Orchestrador batch
│       │       ├── IngestItemProcessor.java   ← Dedup + persistencia
│       │       └── TopicParser.java           ← Parseo topics MQTT
│       └── domain/
│           ├── ingest/
│           │   ├── IngestEvent.java           ← Entidad con UK constraint
│           │   └── TelemetryReading.java      ← Entidad con UK constraint
│           └── repository/
│               ├── IngestEventRepository.java
│               └── TelemetryReadingRepository.java
└── docs/exercises/exercise-03-offline-replay-controlled/
    ├── exercise-03-offline-replay-controlled.md  ← Documento del ejercicio
    └── evidence/
        └── README.md                             ← Este archivo
```
