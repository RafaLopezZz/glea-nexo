# Ejercicio 4 — Trazabilidad temporal completa

## Objetivo

Documentar la trazabilidad temporal de una lectura de telemetría desde su generación en edge hasta su persistencia en backend, distinguiendo claramente entre tiempo de evento, tiempo de recepción, tiempo de proceso y tiempo de persistencia.

## Lectura elegida

Se toma como ejemplo una lectura con `ts` explícito, alineada con el contrato real del simulador y del backend:

- Topic MQTT:
  `agro/finca1/zona1/pi-gw-001/sensor/soil-01/SOIL_MOISTURE/telemetry`

- Payload:

```json
{
  "messageId": "t-001",
  "deviceId": "pi-gw-001",
  "sensorId": "soil-01",
  "type": "SOIL_MOISTURE",
  "ts": "2026-01-01T00:00:00Z",
  "value": 25.0,
  "unit": "%VWC",
  "quality": "good"
}
```

Para este análisis, el timestamp de negocio explícito es:

- `ts = 2026-01-01T00:00:00Z`

---

## Trazabilidad paso a paso

### 1. Simulador / MQTT

El simulador genera el payload con `ts` en UTC y publica en un topic v2 de telemetría.

Hallazgos relevantes:

- El simulador construye `ts` con `datetime.now(timezone.utc).isoformat()`.
- El topic incluye `finca`, `zona`, `gateway/device`, `sensorId`, `sensorType` y el sufijo `telemetry`.

Referencias:

- `edge/python/services/simulator/main.py:11-12`
- `edge/python/services/simulator/main.py:42-45`
- `edge/python/services/simulator/main.py:101-111`
- `edge/python/services/simulator/main.py:141-143`

Interpretación temporal:

- Aquí nace el **event time**.
- MQTT no añade un timestamp de negocio propio; solo transporta topic + payload.

### 2. Node-RED

Node-RED recibe la telemetría MQTT, la parsea, valida y la transforma al contrato HTTP del backend.

#### 2.1 Captura hacia SQLite outbox

En el flujo de entrada al outbox, Node-RED calcula:

- `enqueuedAt = new Date().toISOString()`
- `$ts = p.ts || enqueuedAt`
- `$next_attempt_at = enqueuedAt`

Esto significa:

- Si el payload trae `p.ts`, el campo `outbox.ts` conserva el tiempo de evento.
- Si no lo trae, `outbox.ts` cae a la hora local de encolado.

Referencias:

- `edge/nodered/flows/flows.json:81`
- `edge/nodered/flows/flows.json:202-203`

#### 2.2 Reenvío desde outbox al backend

En el flujo bridge/replay, Node-RED exige que el payload tenga `p.ts`:

- si falta `p.ts`, descarta el mensaje para el POST;
- si existe, lo mapea a `readings[0].ts`.

También envía:

- `source: "edge-nodered"`
- `topic: topic`
- `rawPayload: p`

Referencias:

- `edge/nodered/flows/flows.json:540-541`
- `edge/nodered/flows/flows.json:558-565`
- `edge/nodered/flows/flows.json:730-733`
- `edge/nodered/flows/flows.json:824-825`
- `edge/nodered/flows/flows.json:877-881`
- `edge/nodered/flows/flows.json:902-903`

Interpretación temporal:

- `enqueuedAt` representa el momento en que Node-RED tomó el mensaje para persistirlo localmente.
- `sent_at` representa el momento en que el replay marcó el envío como exitoso en SQLite.
- Ninguno de esos dos tiempos sustituye al `ts` del evento original.

### 3. SQLite outbox

La tabla `outbox` tiene esta semántica temporal:

- `ts`: tiempo asociado al mensaje encolado
- `next_attempt_at`: próximo intento de replay
- `sent_at`: instante en que Node-RED marcó el envío como exitoso

Schema relevante:

- `message_id`
- `ts`
- `topic`
- `payload`
- `state`
- `sent_at`
- `tries`
- `last_error`
- `next_attempt_at`

Referencias:

- `edge/sqlite/schema.sql:8-22`

Interpretación temporal para la lectura elegida:

- `outbox.ts = 2026-01-01T00:00:00Z` si el payload llegó con ese `ts`
- `outbox.next_attempt_at = enqueuedAt`
- `outbox.sent_at = instante de éxito del replay`, si el envío llega al backend

### 4. Backend — IngestEvent

El backend recibe `POST /api/ingest/readings/batch` y procesa cada lectura individualmente.

`IngestEvent` persiste:

- `messageId`
- `topic`
- `source`
- `receivedAt`
- `processedAt`
- `status`
- `rawPayload`

El código asigna explícitamente:

- `ingestEvent.setReceivedAt(Instant.now())`
- `ingestEvent.setStatus(IngestStatus.RECEIVED)`
- tras persistir la telemetría:
  - `ingestEvent.setStatus(IngestStatus.PROCESSED)`
  - `ingestEvent.setProcessedAt(Instant.now())`

Referencias:

- `backend/src/main/java/com/glea/nexo/api/dto/ingest/IngestReadingDto.java:10-21`
- `backend/src/main/java/com/glea/nexo/application/ingest/IngestItemProcessor.java:99-113`
- `backend/src/main/java/com/glea/nexo/application/ingest/IngestItemProcessor.java:185-187`
- `backend/src/main/java/com/glea/nexo/domain/ingest/IngestEvent.java:52-78`

Interpretación temporal:

- `receivedAt` = **received time** en backend
- `processedAt` = **processed time** en backend

### 5. Backend — TelemetryReading

`TelemetryReading` es la persistencia de negocio de la lectura.

Campos temporales relevantes:

- `ts`
- `createdAt`
- `updatedAt`

El código asigna:

```java
telemetry.setTs(reading.ts() != null ? reading.ts() : Instant.now());
```

y luego persiste con `saveAndFlush`.

Referencias:

- `backend/src/main/java/com/glea/nexo/application/ingest/IngestItemProcessor.java:154-171`
- `backend/src/main/java/com/glea/nexo/domain/ingest/TelemetryReading.java:60-91`
- `backend/src/main/java/com/glea/nexo/domain/common/BaseEntityUuid.java:21-27`

Interpretación temporal:

- `TelemetryReading.ts` intenta representar el **event time**
- `TelemetryReading.createdAt` representa mejor el **persisted time** físico de la fila
- `TelemetryReading.updatedAt` representa actualizaciones posteriores, no el tiempo original del evento

## Identificación explícita de tiempos

### Event time

Timestamp canónico del hecho de negocio: cuándo ocurrió la medición.

En esta lectura:

- `event time = 2026-01-01T00:00:00Z`

Se refleja en:

- payload MQTT `ts`
- `outbox.ts` si Node-RED conserva `p.ts`
- `IngestReadingDto.ts`
- `TelemetryReading.ts`

### Received time

Momento en que el backend recibe y registra la ingesta.

Se refleja en:

- `IngestEvent.receivedAt`

Asignación:

- `Instant.now()` al crear el `IngestEvent`

Referencia:

- `backend/src/main/java/com/glea/nexo/application/ingest/IngestItemProcessor.java:107`

### Processed time

Momento en que el backend completa el procesamiento lógico de la lectura.

Se refleja en:

- `IngestEvent.processedAt`

Asignación:

- `Instant.now()` al marcar `PROCESSED`

Referencia:

- `backend/src/main/java/com/glea/nexo/application/ingest/IngestItemProcessor.java:185-187`

### Persisted time

Hay dos niveles útiles:

1. Persistencia local edge:
   - inserción en SQLite outbox
   - no tiene un campo `created_at`, pero `next_attempt_at = enqueuedAt` aproxima ese momento
   - `sent_at` marca éxito de replay, no creación

2. Persistencia backend:
   - `TelemetryReading.createdAt`
   - `IngestEvent.createdAt`

Referencia:

- `backend/src/main/java/com/glea/nexo/domain/common/BaseEntityUuid.java:21-27`

## Ambigüedades y sobrescrituras detectadas

### 1. Ambigüedad en `TelemetryReading.ts`

Código actual:

```java
telemetry.setTs(reading.ts() != null ? reading.ts() : Instant.now());
```

Referencia:

- `backend/src/main/java/com/glea/nexo/application/ingest/IngestItemProcessor.java:164`

Problema:

- el mismo campo `TelemetryReading.ts` puede significar:
  - tiempo real del evento, si el payload trae `ts`
  - tiempo de ingesta/proceso, si el payload no lo trae

Consecuencia:

- se mezcla semántica de negocio con semántica operativa;
- una consulta temporal sobre `TelemetryReading.ts` puede dejar de representar cuándo ocurrió realmente la medición.

### 2. Ambigüedad en `outbox.ts`

Código actual:

```javascript
$ts: p.ts || enqueuedAt
```

Referencia:

- `edge/nodered/flows/flows.json:81`

Problema:

- `outbox.ts` puede ser tiempo de evento o tiempo de encolado local según venga o no `p.ts`.

Consecuencia:

- SQLite outbox no es una fuente temporal totalmente homogénea;
- al auditar una fila aislada no siempre queda claro si `ts` es evento o fallback local.

### 3. Propagación a `sensor.lastSeenAt`

Código actual:

```java
sensor.setLastSeenAt(telemetry.getTs());
```

Referencia:

- `backend/src/main/java/com/glea/nexo/application/ingest/IngestItemProcessor.java:176`

Problema:

- `sensor.lastSeenAt` hereda la ambigüedad de `TelemetryReading.ts`.
- si `telemetry.ts` cayó a `Instant.now()`, `lastSeenAt` ya no expresa “última medición ocurrida”, sino “última medición procesada”.
- incluso cuando `ts` es correcto, `lastSeenAt` puede retroceder o comportarse de forma confusa si llegan mensajes atrasados o replayados.

Consecuencia:

- `lastSeenAt` mezcla potencialmente “último evento observado” con “último evento ingerido”.

### 4. Falta de separación explícita entre tiempo de evento y tiempo de persistencia

Actualmente:

- `TelemetryReading.ts` intenta cubrir el tiempo de negocio;
- `createdAt` existe, pero no siempre se usa explícitamente como tiempo de persistencia;
- en `IngestEvent` sí hay `receivedAt` y `processedAt`, pero no existe un campo de negocio equivalente al `event time`.

Consecuencia:

- el modelo soporta trazabilidad parcial, pero obliga a interpretar convenciones implícitas.

## Recomendación de timestamp canónico de negocio

El timestamp canónico de negocio debería ser:

- `TelemetryReading.ts`, entendido exclusivamente como **event time**
- derivado del `reading.ts` recibido desde edge
- obligatorio en el contrato de entrada para telemetría útil

Justificación:

1. Es el instante en que ocurrió la medición real.
2. Permite análisis históricos correctos aunque haya colas, reintentos o replay.
3. No depende de latencia de red, disponibilidad de backend ni backlog del outbox.
4. Es el tiempo correcto para series temporales, alertas retrospectivas y trazabilidad agronómica.

Regla recomendada:

- `reading.ts` debe ser obligatorio para telemetría.
- `TelemetryReading.ts` no debería usar fallback a `Instant.now()` en producción de negocio.
- Si falta `ts`, conviene rechazar la lectura o marcarla explícitamente como inválida/incompleta.
- `IngestEvent.receivedAt` y `IngestEvent.processedAt` deben conservarse como tiempos operativos.
- `createdAt` debe usarse como tiempo físico de persistencia, no como tiempo de negocio.

## Conclusión

La trazabilidad temporal del repo ya distingue razonablemente el plano operativo del backend (`receivedAt`, `processedAt`, `createdAt`) del plano de negocio (`TelemetryReading.ts`), pero todavía hay dos ambigüedades importantes:

- `TelemetryReading.ts` puede degradarse a `Instant.now()` si falta `reading.ts`.
- `outbox.ts` puede degradarse a `enqueuedAt` si falta `p.ts`.

La decisión más sólida es considerar como timestamp canónico de negocio el `ts` originado en el payload del sensor y persistido en `TelemetryReading.ts`, dejando:

- `IngestEvent.receivedAt` como tiempo de recepción,
- `IngestEvent.processedAt` como tiempo de proceso,
- `createdAt` de las entidades como tiempo de persistencia física,
- `sent_at` en SQLite como tiempo de éxito del replay edge.

En otras palabras: para responder “cuándo ocurrió la medición”, la respuesta correcta debe salir del `ts` del evento, no de los relojes de Node-RED ni del backend.
