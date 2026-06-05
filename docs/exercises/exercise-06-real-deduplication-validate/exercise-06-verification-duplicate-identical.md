# Verificación manual — Duplicado idéntico

## Objetivo

Dejar evidencia de la prueba manual del miniejercicio 2 del Ejercicio 6: enviar dos veces exactamente el mismo batch y comprobar que la deduplicación funciona en API, logs y base de datos.

## Entorno de prueba

- Origen del POST: Raspberry Pi
- Backend: `http://192.168.1.10:8080`
- Endpoint: `POST /api/ingest/readings/batch`
- Payload probado: `dedupe-001.json`

## Payload utilizado

La prueba se hizo con el mismo payload en ambos envíos:

```json
{
  "source": "manual-dedupe-test-rpi",
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

## Evidencia de ejecución

### Primer POST

Comando ejecutado desde la Raspberry Pi:

```bash
curl -s -X POST "http://192.168.1.10:8080/api/ingest/readings/batch" \
  -H "Content-Type: application/json" \
  --data @dedupe-001.json
```

Resultado observado:

```json
{"total":1,"processed":1,"duplicates":0,"errors":0}
```

Interpretación:

- la lectura fue aceptada;
- se persistió una telemetría funcional;
- no se detectó duplicado en el primer envío.

### Segundo POST idéntico

Se repitió exactamente el mismo comando y el mismo fichero:

```bash
curl -s -X POST "http://192.168.1.10:8080/api/ingest/readings/batch" \
  -H "Content-Type: application/json" \
  --data @dedupe-001.json
```

Resultado observado:

```json
{"total":1,"processed":0,"duplicates":1,"errors":0,"items":[{"index":0,"messageId":"dedupe-001","status":"DUPLICATE"}]}
```

Interpretación:

- el backend reconoció el segundo envío como duplicado;
- no volvió a procesar la lectura;
- la deduplicación se activó antes de generar una segunda persistencia útil.

## Evidencia en logs del backend

Logs observados:

```text
Telemetry persisted: messageId=dedupe-001, gatewayUid=pi-gw-001, sensorUid=soil-01, value=25.0
ingest duplicate pre-check messageId=dedupe-001 gatewayUid=pi-gw-001
```

Interpretación:

- el primer request llegó hasta persistir `TelemetryReading`;
- el segundo request fue frenado por el pre-check de `IngestEvent`;
- el duplicado no se detectó por una caída posterior ni por error interno, sino por la lógica normal de idempotencia.

## Evidencia en SQL

### Verificación en `ingest_event`

Consulta ejecutada:

```sql
select device_id, message_id, count(*) as total
from ingest_event
where message_id = 'dedupe-001'
group by device_id, message_id;
```

Resultado observado:

- `device_id = e7ddfd32-8c1a-49c8-880d-bf95c3214e32`
- `message_id = dedupe-001`
- `total = 1`

Interpretación:

- existe una sola fila para la clave `device_id + message_id`;
- no se creó un segundo `IngestEvent` para el duplicado idéntico.

### Verificación en `telemetry_reading`

Consulta ejecutada:

```sql
select sensor_id, message_id, count(*) as total
from telemetry_reading
where message_id = 'dedupe-001'
group by sensor_id, message_id;
```

Resultado observado:

- `sensor_id = 9471597a-7498-48f9-ae51-3686ddcca1ec`
- `message_id = dedupe-001`
- `total = 1`

Interpretación:

- existe una sola fila para la clave `sensor_id + message_id`;
- no se creó una segunda `TelemetryReading` para el duplicado idéntico.

## Veredicto de esta prueba

La prueba manual del duplicado idéntico fue satisfactoria.

Se verificó que:

- el primer envío se procesa normalmente;
- el segundo envío se clasifica como duplicado;
- en `ingest_event` sigue existiendo una sola fila para `device_id + message_id`;
- en `telemetry_reading` sigue existiendo una sola fila para `sensor_id + message_id`.

## Conclusión parcial del Ejercicio 6

Para el caso de reenvío idéntico, la deduplicación actual se comporta de forma correcta y está respaldada por:

- pre-check en aplicación;
- evidencia en respuesta HTTP;
- evidencia en logs del backend;
- evidencia en SQL.

Esto todavía no cierra la clasificación final del ejercicio, porque aún falta probar el caso más delicado:

- mismo `messageId`
- mismo scope de deduplicación
- pero payload distinto

Ese escenario se documenta en el siguiente runbook.
