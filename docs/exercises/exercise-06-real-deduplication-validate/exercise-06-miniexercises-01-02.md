# Ejercicio 6 — Validar deduplicación real

## Objetivo

Documentar cómo funciona la deduplicación real en el backend y cómo validarla enviando exactamente el mismo batch dos veces.

## Miniejercicio 1 — Claves y constraints reales

La deduplicación no depende de un `messageId` globalmente único para todo el sistema. Depende de claves compuestas distintas según la entidad persistida.

### IngestEvent

`IngestEvent` representa el evento de ingestión a nivel de gateway/dispositivo.

- Clave real de deduplicación: `device_id + message_id`
- Pre-check en código: `existsByDevice_IdAndMessageId(...)`
- Constraint único en BD: `uk_ingest_device_message`

Implicación práctica:

- el mismo `messageId` puede existir en otro dispositivo sin conflicto;
- el duplicado se detecta solo si coincide con el mismo `device_id`.

### TelemetryReading

`TelemetryReading` representa la lectura de telemetría a nivel de sensor.

- Clave real de deduplicación: `sensor_id + message_id`
- Pre-check en código: `existsBySensor_IdAndMessageId(...)`
- Constraint único en BD: `uk_reading_sensor_message`

Implicación práctica:

- el mismo `messageId` puede existir en otro sensor sin conflicto;
- el duplicado se detecta solo si coincide con el mismo `sensor_id`.

### Resumen

| Entidad | Clave real | Pre-check en código | Unique constraint en BD |
|---|---|---|---|
| `IngestEvent` | `device_id + message_id` | `existsByDevice_IdAndMessageId(...)` | `uk_ingest_device_message` |
| `TelemetryReading` | `sensor_id + message_id` | `existsBySensor_IdAndMessageId(...)` | `uk_reading_sensor_message` |

### Red de seguridad ante concurrencia

Además del pre-check, el backend captura `DataIntegrityViolationException`.

Eso actúa como red de seguridad si dos procesos pasan el pre-check casi al mismo tiempo y el duplicado termina siendo rechazado por la constraint única de base de datos.

### Conclusión del miniejercicio 1

`messageId` no es globalmente único por sí solo.

La unicidad real es contextual:

- en `IngestEvent`, dentro del dispositivo;
- en `TelemetryReading`, dentro del sensor.

## Miniejercicio 2 — Enviar el mismo batch dos veces

### Qué significa “batch idéntico”

Un batch idéntico significa reenviar exactamente el mismo request:

- mismo `topic` v2;
- mismo `source` si aplica;
- misma lista `readings`;
- mismo `messageId`;
- mismo `deviceId`;
- mismo `ts`;
- mismo `value`, `unit`, `battery`, `rssi`;
- mismo orden de items.

No basta con “parecido”. Debe ser el mismo payload lógico.

### Qué debe pasar

#### Primer envío

Si la lectura no existía antes:

- se crea un `IngestEvent`;
- se crea un `TelemetryReading`;
- la respuesta debe reflejar procesamiento normal.

Esperable:

- `processed = 1`
- `duplicates = 0`
- `errors = 0`

#### Segundo envío del mismo batch

Como la combinación ya existe:

- no debe insertarse un nuevo `IngestEvent` para ese mismo `device_id + message_id`;
- no debe insertarse un nuevo `TelemetryReading` para ese mismo `sensor_id + message_id`;
- la respuesta debe marcar el item como duplicado.

Esperable:

- `processed = 0`
- `duplicates = 1`
- `errors = 0`

### Qué se espera en `IngestEvent`

Tras dos envíos idénticos, debe seguir existiendo solo un registro para esa clave lógica:

- mismo dispositivo
- mismo `messageId`

### Qué se espera en `TelemetryReading`

Tras dos envíos idénticos, debe seguir existiendo solo una lectura para esa clave lógica:

- mismo sensor
- mismo `messageId`

## Cómo probarlo

### 1. Verificar que el backend esté arriba

```bash
curl -s http://localhost:8080/api/ping
curl -s http://localhost:8080/actuator/health
```

### 2. Preparar un payload con topic v2 y `messageId` fijo

Ejemplo compatible con el contrato actual:

```json
{
  "source": "manual-dedupe-test",
  "topic": "agro/finca1/zona1/pi-gw-001/sensor/soil-01/SOIL_MOISTURE/telemetry",
  "readings": [
    {
      "messageId": "dedupe-001",
      "deviceId": "pi-gw-001:soil-01",
      "ts": "2026-01-01T00:00:00Z",
      "value": 25.0,
      "unit": "%VWC",
      "rssi": -55,
      "battery": 3.78
    }
  ]
}
```

Notas importantes:

- `ts` debe ser válido y en ISO-8601 UTC.
- el topic debe ser v2 e incluir gateway y sensor:
  `agro/{finca}/{zona}/{gatewayUid}/sensor/{sensorUid}/{TYPE}/telemetry`

### 3. Enviar el mismo batch por primera vez

PowerShell:

```powershell
$body = @'
{
  "source": "manual-dedupe-test",
  "topic": "agro/finca1/zona1/pi-gw-001/sensor/soil-01/SOIL_MOISTURE/telemetry",
  "readings": [
    {
      "messageId": "dedupe-001",
      "deviceId": "pi-gw-001:soil-01",
      "ts": "2026-01-01T00:00:00Z",
      "value": 25.0,
      "unit": "%VWC",
      "rssi": -55,
      "battery": 3.78
    }
  ]
}
'@

curl -s -X POST "http://localhost:8080/api/ingest/readings/batch" `
  -H "Content-Type: application/json" `
  -d $body
```

### 4. Enviar exactamente el mismo batch por segunda vez

Repetir exactamente el mismo comando:

```powershell
curl -s -X POST "http://localhost:8080/api/ingest/readings/batch" `
  -H "Content-Type: application/json" `
  -d $body
```

## Cómo validar la deduplicación

## Validación por respuesta HTTP

### Respuesta esperada del primer request

Debe indicar procesamiento exitoso del item. Un resultado típico sería:

```json
{
  "total": 1,
  "processed": 1,
  "duplicates": 0,
  "errors": 0,
  "items": [
    {
      "index": 0,
      "messageId": "dedupe-001",
      "status": "PROCESSED",
      "detail": "telemetry reading persisted"
    }
  ]
}
```

### Respuesta esperada del segundo request

Debe indicar duplicado. Un resultado típico sería:

```json
{
  "total": 1,
  "processed": 0,
  "duplicates": 1,
  "errors": 0,
  "items": [
    {
      "index": 0,
      "messageId": "dedupe-001",
      "status": "DUPLICATE",
      "detail": "duplicate ingest event by exists check"
    }
  ]
}
```

Dependiendo del punto exacto donde se detecte el duplicado, el `detail` puede variar, pero el resultado funcional esperado es el mismo: no se inserta una segunda vez.

## Validación en logs/backend

Conviene revisar logs del backend para confirmar si el duplicado fue detectado por:

- pre-check de `IngestEvent`;
- pre-check de `TelemetryReading`;
- o constraint única como red de seguridad.

Ejemplo:

```bash
docker compose -f infra/compose/docker-compose.platform.yml logs -f backend
```

Indicadores esperables:

- `ingest duplicate pre-check`
- `Telemetry duplicate pre-check`
- `Duplicate by unique constraint`

## Validación en SQL

Conexión de referencia:

- host: `localhost`
- puerto: `3609`
- db: `glea_nexo`
- usuario: `glea`
- password: `glea_123`

### Verificar `ingest_event`

```sql
select
  ie.id,
  ie.message_id,
  ie.device_id,
  ie.status,
  ie.received_at,
  ie.processed_at
from ingest_event ie
where ie.message_id = 'dedupe-001'
order by ie.received_at desc;
```

Resultado esperado:

- 1 sola fila para ese `message_id` y ese `device_id`.

Chequeo agregado:

```sql
select
  ie.device_id,
  ie.message_id,
  count(*) as total
from ingest_event ie
where ie.message_id = 'dedupe-001'
group by ie.device_id, ie.message_id;
```

Resultado esperado:

- `total = 1`

### Verificar `telemetry_reading`

```sql
select
  tr.id,
  tr.message_id,
  tr.sensor_id,
  tr.device_id,
  tr.ts,
  tr.value_num
from telemetry_reading tr
where tr.message_id = 'dedupe-001'
order by tr.ts desc;
```

Resultado esperado:

- 1 sola fila para ese `message_id` y ese `sensor_id`.

Chequeo agregado:

```sql
select
  tr.sensor_id,
  tr.message_id,
  count(*) as total
from telemetry_reading tr
where tr.message_id = 'dedupe-001'
group by tr.sensor_id, tr.message_id;
```

Resultado esperado:

- `total = 1`

## Confirmación de dedupe correcta

La deduplicación queda validada correctamente si se cumple todo esto:

- primer request: `processed = 1`
- segundo request: `duplicates = 1`
- en `ingest_event` sigue habiendo una sola fila para la clave `device_id + message_id`
- en `telemetry_reading` sigue habiendo una sola fila para la clave `sensor_id + message_id`

## Qué NO demuestra todavía

Este miniejercicio todavía no demuestra el comportamiento de un caso importante:

- mismo `messageId`
- mismo scope de deduplicación
- pero payload distinto

Ese escenario sirve para validar si el sistema trata la deduplicación como idempotencia estricta por clave o si compara contenido. Ese análisis queda pendiente para un paso posterior.
