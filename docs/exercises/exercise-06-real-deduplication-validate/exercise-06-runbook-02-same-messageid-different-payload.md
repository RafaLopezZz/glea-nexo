# Runbook 2 — Mismo `messageId`, payload distinto

## Objetivo

Probar el caso más delicado del Ejercicio 6: reenviar un mensaje con el mismo `messageId`, pero cambiando parte del payload, para observar si el sistema lo rechaza, lo ignora o introduce algún riesgo semántico.

## Qué queremos aprender

Esta prueba no busca confirmar el caso fácil de duplicado idéntico. Busca responder esto:

> Si el productor reutiliza el mismo `messageId` para un contenido distinto, ¿qué hace realmente el sistema?

## Hipótesis previa

Con la implementación actual, la deduplicación se basa en:

- `device_id + message_id` en `IngestEvent`
- `sensor_id + message_id` en `TelemetryReading`

Por tanto, si el segundo request mantiene:

- el mismo gateway/dispositivo,
- el mismo sensor,
- y el mismo `messageId`,

pero cambia el contenido, lo esperable es que el sistema lo trate como duplicado igualmente.

Eso significa que la deduplicación probablemente se basa en identidad por clave, no en comparación de contenido.

## Precondiciones

- Backend accesible desde la Raspberry Pi
- Primera prueba del duplicado idéntico ya ejecutada o entorno limpio
- Topic v2 válido
- `ts` válido en formato ISO-8601 UTC

## Escenario recomendado

Mantener constantes:

- `messageId`
- `topic`
- `deviceId`
- `sensor`

Cambiar solo un dato funcional, por ejemplo:

- `value`

## Paso 1 — Crear payload base

Guardar como `dedupe-conflict-001-a.json`:

```bash
cat > dedupe-conflict-001-a.json <<'EOF'
{
  "source": "manual-dedupe-test-rpi",
  "topic": "agro/finca1/zona1/pi-gw-001/sensor/soil-01/SOIL_MOISTURE/telemetry",
  "readings": [
    {
      "messageId": "dedupe-conflict-001",
      "deviceId": "pi-gw-001:soil-01",
      "ts": "2026-01-01T00:00:00Z",
      "value": 25.0,
      "unit": "%VWC",
      "rssi": -55,
      "battery": 3.78
    }
  ]
}
EOF
```

## Paso 2 — Crear payload conflictivo

Guardar como `dedupe-conflict-001-b.json`:

```bash
cat > dedupe-conflict-001-b.json <<'EOF'
{
  "source": "manual-dedupe-test-rpi",
  "topic": "agro/finca1/zona1/pi-gw-001/sensor/soil-01/SOIL_MOISTURE/telemetry",
  "readings": [
    {
      "messageId": "dedupe-conflict-001",
      "deviceId": "pi-gw-001:soil-01",
      "ts": "2026-01-01T00:00:00Z",
      "value": 31.5,
      "unit": "%VWC",
      "rssi": -55,
      "battery": 3.78
    }
  ]
}
EOF
```

La única diferencia aquí es `value`.

## Paso 3 — Enviar el payload base

```bash
curl -s -X POST "http://192.168.1.10:8080/api/ingest/readings/batch" \
  -H "Content-Type: application/json" \
  --data @dedupe-conflict-001-a.json
```

### Esperado

- `processed = 1`
- `duplicates = 0`
- `errors = 0`

## Paso 4 — Enviar el payload conflictivo con el mismo `messageId`

```bash
curl -s -X POST "http://192.168.1.10:8080/api/ingest/readings/batch" \
  -H "Content-Type: application/json" \
  --data @dedupe-conflict-001-b.json
```

### Hipótesis esperada

Lo más probable es:

- `processed = 0`
- `duplicates = 1`
- `errors = 0`

porque el backend deduplica por clave, no por comparación profunda de contenido.

## Paso 5 — Validar en logs

```bash
docker compose -f infra/compose/docker-compose.platform.yml logs -f backend
```

### Qué observar

- si el segundo request cae en `ingest duplicate pre-check`
- si aparece algún detalle sobre conflicto de contenido

## Paso 6 — Validar en SQL

### `ingest_event`

```sql
select
  device_id,
  message_id,
  count(*) as total
from ingest_event
where message_id = 'dedupe-conflict-001'
group by device_id, message_id;
```

### `telemetry_reading`

```sql
select
  sensor_id,
  message_id,
  count(*) as total,
  min(value_num) as min_value,
  max(value_num) as max_value
from telemetry_reading
where message_id = 'dedupe-conflict-001'
group by sensor_id, message_id;
```

## Resultado esperado

Si la deduplicación está funcionando por clave estricta:

- solo habrá una fila en `ingest_event`
- solo habrá una fila en `telemetry_reading`
- el valor persistido será el del primer request
- el segundo payload, aunque distinto, habrá sido absorbido como duplicado

## Cómo interpretar el resultado

### Caso A — El segundo request cae como duplicado y solo queda la primera fila

Interpretación:

- la dedupe funciona por identidad de clave;
- el sistema no compara contenido;
- existe riesgo residual si un productor reutiliza mal `messageId`.

### Caso B — Se inserta una segunda fila

Interpretación:

- la dedupe sería más débil de lo esperado;
- habría que revisar si cambió el scope real de identidad.

### Caso C — Falla con error distinto a duplicate

Interpretación:

- habría que revisar si la validación, el parser o algún constraint adicional interfiere.

## Riesgo que esta prueba quiere dejar visible

Aunque el duplicado idéntico esté bien resuelto, todavía puede existir este riesgo:

> si un productor reutiliza el mismo `messageId` para un payload distinto, el sistema podría ignorar el segundo contenido sin detectar conflicto semántico.

Ese riesgo residual debe quedar escrito al cerrar el ejercicio.
