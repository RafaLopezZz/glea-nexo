# Ejercicio 7 — OpenAPI como contrato vivo

## Miniejercicio 1 — Inventariar endpoints existentes

## Objetivo

Construir el inventario real de endpoints expuestos por el backend para que Swagger/OpenAPI describa la API existente de verdad y no una versión supuesta o recordada de memoria.

## Criterio usado

Para este inventario se tomaron únicamente los controladores realmente presentes en:

- `backend/src/main/java/com/glea/nexo/api/controller`
- `backend/src/main/java/com/glea/nexo/api/controller/inventory`

No se documentan endpoints no confirmados en código.

## Controladores encontrados

### Raíz `api/controller`

- `PingController`
- `IngestController`
- `TelemetryController`
- `AlertController`

### Subdirectorio `api/controller/inventory`

- `FarmController`
- `ZoneController`
- `DeviceController`

## Inventario real de endpoints

| Método | Ruta | Controller | Propósito |
|---|---|---|---|
| GET | `/api/ping` | `PingController` | Check simple de disponibilidad |
| POST | `/api/ingest/readings/batch` | `IngestController` | Ingesta batch de telemetría |
| GET | `/api/telemetry/readings` | `TelemetryController` | Histórico de lecturas |
| GET | `/api/telemetry/latest` | `TelemetryController` | Último snapshot por sensor |
| GET | `/api/alerts` | `AlertController` | Alertas operativas |
| POST | `/api/farms` | `FarmController` | Crear finca |
| GET | `/api/farms` | `FarmController` | Listar fincas |
| GET | `/api/farms/{farmId}` | `FarmController` | Obtener finca |
| PUT | `/api/farms/{farmId}` | `FarmController` | Actualizar finca |
| DELETE | `/api/farms/{farmId}` | `FarmController` | Eliminar finca |
| POST | `/api/farms/{farmId}/zones` | `ZoneController` | Crear zona bajo finca |
| GET | `/api/farms/{farmId}/zones` | `ZoneController` | Listar zonas de una finca |
| GET | `/api/zones/{zoneId}` | `ZoneController` | Obtener zona |
| PUT | `/api/zones/{zoneId}` | `ZoneController` | Actualizar zona |
| DELETE | `/api/zones/{zoneId}` | `ZoneController` | Eliminar zona |
| POST | `/api/zones/{zoneId}/devices` | `DeviceController` | Crear dispositivo bajo zona |
| GET | `/api/devices` | `DeviceController` | Listar dispositivos |
| GET | `/api/devices/{deviceId}` | `DeviceController` | Obtener dispositivo |
| PUT | `/api/devices/{deviceId}` | `DeviceController` | Actualizar dispositivo |
| DELETE | `/api/devices/{deviceId}` | `DeviceController` | Eliminar dispositivo |

## Observaciones por área

### Health

- `PingController` expone `GET /api/ping`.
- Es útil para pruebas rápidas de disponibilidad.
- A priori está menos documentado en OpenAPI que el resto.

### Ingest

- `IngestController` expone `POST /api/ingest/readings/batch`.
- Es el endpoint crítico de entrada de telemetría.
- Ya debe reflejar las reglas cerradas en Bloque 2, especialmente `ts` obligatorio como `event time`.

### Telemetry

- `TelemetryController` expone histórico y latest.
- Ambos endpoints usan filtros temporales `from` y `to`.
- Estos endpoints ya están directamente conectados con el endurecimiento del contrato temporal del Ejercicio 5.

### Alerts

- `AlertController` expone `GET /api/alerts`.
- Comparte filtros temporales con telemetría.
- También debe reflejar las reglas de rango máximo y validación temporal.

### Inventory

- Los controladores de `inventory` amplían mucho el alcance del ejercicio.
- El inventario real no se limita a observabilidad: también incluye CRUD de farms, zones y devices.
- Por tanto, Swagger debe cubrir tanto la parte de telemetría como la de inventario.

## Conclusión del miniejercicio 1

El inventario real de la API incluye endpoints de:

- health,
- ingest,
- telemetry,
- alerts,
- y CRUD de inventario (`farms`, `zones`, `devices`).

La implicación para el Ejercicio 7 es clara: OpenAPI no debe documentar solo la parte de telemetría. Debe convertirse en contrato vivo para toda la API expuesta por el backend.

## Siguiente paso natural

Una vez inventariados los endpoints reales, el siguiente trabajo consiste en revisar qué parámetros críticos, ejemplos y respuestas faltan en Swagger, especialmente:

- `X-Org-Code`
- filtros
- paginación
- fechas
- ejemplos reales de request/response
- errores documentados
