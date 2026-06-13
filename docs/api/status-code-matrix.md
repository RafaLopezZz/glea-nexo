# API - Status code matrix

## Objetivo

Matriz base para el **Ejercicio 8**: alinear `status codes`, formato de error y casos esperados sin depender de memoria tácita.

## Formato estándar de error

Todos los errores controlados deben responder con `ApiErrorResponse`:

```json
{
  "timestamp": "2026-02-15T22:00:00Z",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "path": "/api/farms",
  "correlationId": "rbk-001",
  "fieldErrors": [
    {
      "field": "code",
      "message": "must not be blank"
    }
  ]
}
```

## Matriz endpoint → caso → status esperado

| Endpoint | Caso | Status | `error` | Nota |
| --- | --- | ---: | --- | --- |
| `POST /api/farms` | body válido | `201` | — | crea farm |
| `POST /api/farms` | `code`/`name` inválidos | `400` | `VALIDATION_ERROR` | con `fieldErrors` |
| `POST /api/farms` | `code` duplicado en misma org | `409` | `CONFLICT` | constraint de unicidad |
| `GET /api/farms/{farmId}` | `farmId` inexistente | `404` | `NOT_FOUND` | recurso no existe |
| `PUT /api/farms/{farmId}` | body inválido | `400` | `VALIDATION_ERROR` | validación Bean Validation |
| `PUT /api/farms/{farmId}` | `farmId` inexistente | `404` | `NOT_FOUND` | recurso no existe |
| `DELETE /api/farms/{farmId}` | referencias activas | `409` | `CONFLICT` | conflicto relacional |
| `POST /api/farms/{farmId}/zones` | `farmId` inexistente | `404` | `NOT_FOUND` | farm no existe |
| `POST /api/farms/{farmId}/zones` | `code` duplicado en misma farm | `409` | `CONFLICT` | constraint de unicidad |
| `GET /api/zones/{zoneId}` | `zoneId` inexistente | `404` | `NOT_FOUND` | recurso no existe |
| `POST /api/zones/{zoneId}/devices` | `zoneId` inexistente | `404` | `NOT_FOUND` | zone no existe |
| `POST /api/zones/{zoneId}/devices` | `deviceUid` duplicado en misma org | `409` | `CONFLICT` | constraint de unicidad |
| `PUT /api/devices/{deviceId}` | body con `deviceUid` | `400` | `VALIDATION_ERROR` | propiedad no permitida |
| `PUT /api/devices/{deviceId}` | `deviceId` inexistente | `404` | `NOT_FOUND` | recurso no existe |
| `POST /api/ingest/readings/batch` | batch válido | `200` | — | procesa o deduplica |
| `POST /api/ingest/readings/batch` | `ts` faltante/mal formado | `400` | `VALIDATION_ERROR` | con `fieldErrors` o mensaje |
| `GET /api/telemetry/readings` | `from > to` | `400` | `VALIDATION_ERROR` | rango inválido |
| `GET /api/telemetry/readings` | rango > 2 años | `400` | `VALIDATION_ERROR` | límite defensivo |
| `GET /api/alerts` | rango > 2 años | `400` | `VALIDATION_ERROR` | límite defensivo |
| `*` | error inesperado | `500` | `INTERNAL_ERROR` | fallback de `GlobalExceptionHandler` |

## Ejemplos rápidos por familia de error

### 400 — validation

```json
{
  "timestamp": "2026-02-15T22:00:00Z",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "path": "/api/farms",
  "correlationId": "corr-400",
  "fieldErrors": [
    {
      "field": "code",
      "message": "must not be blank"
    }
  ]
}
```

### 404 — not found

```json
{
  "timestamp": "2026-02-15T22:00:00Z",
  "status": 404,
  "error": "NOT_FOUND",
  "message": "Farm not found",
  "path": "/api/farms/00000000-0000-0000-0000-000000000000",
  "correlationId": "corr-404",
  "fieldErrors": []
}
```

### 409 — conflict

```json
{
  "timestamp": "2026-02-15T22:00:00Z",
  "status": 409,
  "error": "CONFLICT",
  "message": "Unique or relational constraint violation",
  "path": "/api/farms",
  "correlationId": "corr-409",
  "fieldErrors": []
}
```

### 500 — internal error

```json
{
  "timestamp": "2026-02-15T22:00:00Z",
  "status": 500,
  "error": "INTERNAL_ERROR",
  "message": "Unexpected server error",
  "path": "/api/any-endpoint",
  "correlationId": "corr-500",
  "fieldErrors": []
}
```
