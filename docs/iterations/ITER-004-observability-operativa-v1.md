# ITER-004 — Observabilidad operativa v1

## Resumen
Se implementan los contratos backend mínimos para la pantalla de observabilidad v1:
- `GET /api/telemetry/readings`
- `GET /api/telemetry/latest`
- `GET /api/alerts`

## Filtros soportados
Comunes a los tres endpoints:
- `zoneId`
- `deviceId`
- `from`
- `to`

Regla de validación:
- si `from > to` la API responde `400 VALIDATION_ERROR`.

## Contratos JSON mínimos

### 1) GET `/api/telemetry/readings`
Serie simple para gráfico y tabla corta.

Ejemplo:
```json
[
  {
    "readingId": "2e261c54-2a68-4689-9f31-18a5bf938b30",
    "ts": "2026-03-29T10:05:00Z",
    "zoneId": "9eb7fcad-e390-4a01-b40b-535fb7da9b66",
    "deviceId": "c7372d4b-d764-4638-9f17-b13c6d5c8526",
    "deviceUid": "gw-01",
    "sensorId": "1d65e985-7035-4d06-a776-91e9cc7bcebf",
    "sensorUid": "sensor-temp-01",
    "sensorType": "TEMPERATURE",
    "value": 22.1,
    "unit": "C",
    "quality": "UNKNOWN",
    "battery": 3.9,
    "rssi": -67
  }
]
```

### 2) GET `/api/telemetry/latest`
Último valor por sensor con snapshot operativo del dispositivo.

Ejemplo:
```json
[
  {
    "zoneId": "9eb7fcad-e390-4a01-b40b-535fb7da9b66",
    "deviceId": "c7372d4b-d764-4638-9f17-b13c6d5c8526",
    "deviceUid": "gw-01",
    "deviceState": "ONLINE",
    "deviceBattery": 3.9,
    "deviceRssi": -67,
    "sensorId": "1d65e985-7035-4d06-a776-91e9cc7bcebf",
    "sensorUid": "sensor-temp-01",
    "sensorType": "TEMPERATURE",
    "lastTs": "2026-03-29T10:05:00Z",
    "value": 22.1,
    "unit": "C",
    "quality": "UNKNOWN"
  }
]
```

### 3) GET `/api/alerts`
Solo alertas derivadas de dispositivo stale/offline en v1.

Ejemplo:
```json
[
  {
    "type": "DEVICE_STALE",
    "severity": "WARN",
    "alertTs": "2026-03-29T10:05:00Z",
    "zoneId": "9eb7fcad-e390-4a01-b40b-535fb7da9b66",
    "deviceId": "c7372d4b-d764-4638-9f17-b13c6d5c8526",
    "deviceUid": "gw-01",
    "sensorId": null,
    "sensorUid": null,
    "message": "Device gw-01 sin telemetría reciente desde 2026-03-29T10:05:00Z"
  }
]
```

## Decisiones v1
- `readings` cubre gráfico y tabla pequeña. No se abre `/series` aparte.
- `latest` devuelve último valor por sensor, no agregados por zona.
- `alerts` no persiste eventos todavía: deriva alertas de `device.lastSeenAt`.
- Umbral stale fijo: **15 minutos**.

## Límites v1
- Sin realtime/websocket.
- Sin JWT/roles nuevos.
- Sin motor de reglas de umbral en backend.
- Sin paginación ni agregación temporal avanzada.
- Si frontend pide tabla rica o buckets temporales, eso va a v1.1.

## Verificación
```bash
cd backend
mvn test
```
