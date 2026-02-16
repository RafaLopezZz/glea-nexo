# API - Inventory OpenAPI

## OpenAPI endpoints

- JSON OpenAPI: `http://localhost:8080/v3/api-docs`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`

## Header transversal

- `X-Org-Code` (opcional): scope tenant soft. Si falta, usa `default`.
- `X-Correlation-Id` (opcional): trazabilidad request/response/logs.

## Farms

- `POST /api/farms`
- `GET /api/farms?page&size&sort&q?`
- `GET /api/farms/{farmId}`
- `PUT /api/farms/{farmId}`
- `DELETE /api/farms/{farmId}`

### Request ejemplo (POST)

```json
{
  "code": "finca-01",
  "name": "Finca Norte",
  "location": {"lat": 4.71, "lng": -74.07}
}
```

## Zones

- `POST /api/farms/{farmId}/zones`
- `GET /api/farms/{farmId}/zones?page&size&sort&q?`
- `GET /api/zones/{zoneId}`
- `PUT /api/zones/{zoneId}`
- `DELETE /api/zones/{zoneId}`

### Request ejemplo (POST)

```json
{
  "code": "zona-a",
  "name": "Zona A",
  "geometry": {"type": "Polygon", "coordinates": []}
}
```

## Devices

- `POST /api/zones/{zoneId}/devices`
- `GET /api/devices?page&size&sort&farmId?&zoneId?&state?&q?`
- `GET /api/devices/{deviceId}`
- `PUT /api/devices/{deviceId}`
- `DELETE /api/devices/{deviceId}`

### Request ejemplo (POST)

```json
{
  "deviceUid": "gw-001",
  "name": "Gateway 001"
}
```

### Request ejemplo (PUT)

```json
{
  "name": "Gateway 001 Renombrado",
  "state": "ONLINE"
}
```

### Regla de contrato (P0)

- `deviceUid` es **inmutable** después de creación.
- `PUT /api/devices/{deviceId}` solo permite actualizar `name` y `state`.
- Si el cliente envía `deviceUid` en el body de update, la API responde `400 VALIDATION_ERROR` (unknown property).

## Status codes por operación

- `201`: creado
- `200`: lectura/listado/actualización
- `204`: borrado
- `400`: validación (`VALIDATION_ERROR`)
- `404`: no encontrado (`NOT_FOUND`)
- `409`: conflicto (`CONFLICT`)
- `500`: interno (`INTERNAL_ERROR`)

## Error response estándar

```json
{
  "timestamp": "2026-02-15T22:00:00Z",
  "status": 409,
  "error": "CONFLICT",
  "message": "Unique or relational constraint violation",
  "path": "/api/farms",
  "correlationId": "rbk-001",
  "fieldErrors": []
}
```
