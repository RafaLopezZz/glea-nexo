# Backend Scaffold IoT - Estado Actual (post ITER-001)

> Documento actualizado al estado real del repo en **14/02/2026**.

## 1. Vista general

El backend ya implementa un flujo de ingest batch funcional para `IngestEvent`:

1. Recibe `POST /api/ingest/readings/batch`.
2. Valida request y lecturas.
3. Resuelve `topic` por lectura (o fallback a `request.topic`).
4. Parsea contexto MQTT (`farm`, `zone`, `type`) con `TopicParser`.
5. Resuelve/crea inventario mínimo:
   - `Organization` default
   - `Farm`
   - `Zone`
   - `Device`
6. Persiste `IngestEvent`.
7. Maneja deduplicación con doble capa:
   - pre-check `existsByDevice_IdAndMessageId`
   - guardián final: UNIQUE constraint `uk_ingest_device_message`
8. Responde batch itemizado (`PROCESSED`, `DUPLICATE`, `ERROR`) con contadores.

## 2. Qué quedó implementado en ITER-001

### 2.1 API

- `IngestController`:
  - Endpoint: `POST /api/ingest/readings/batch`
  - Entrada validada con `@Valid`
  - Salida: `IngestBatchResponseDto`

DTOs activos:
- `IngestBatchRequestDto`
- `IngestReadingDto`
- `IngestBatchResponseDto`
- `IngestBatchItemResponseDto`

### 2.2 Servicio de ingest

- `IngestServiceImpl`:
  - Valida defensivamente que `readings` no esté vacío.
  - Itera por item y captura errores por lectura para no tumbar todo el batch.
- `IngestItemProcessor`:
  - Ejecuta cada item con `@Transactional(REQUIRES_NEW)`.
  - Crea/recupera `Organization/Farm/Zone/Device`.
  - Inserta `IngestEvent`.
  - Detecta duplicados por pre-check y por excepción de integridad.

### 2.3 Parseo de topic

- `TopicParser` exige topic no vacío y formato base `agro/...`.
- Extrae:
  - `farmCode`
  - `zoneCode`
  - `type`
  - `deviceUidFromTopic` (si viene en segmentos posteriores)
- Si `reading.topic` no viene, usa `request.topic`.

## 3. Persistencia y dedupe

### 3.1 Dedupe efectivo

Se usa estrategia robusta en dos niveles:

1. **Optimización lógica**: `existsByDevice_IdAndMessageId(...)`.
2. **Garantía real de concurrencia**: UNIQUE en BD (`device_id`, `message_id`) capturada con `DataIntegrityViolationException`.

Esto protege ante carreras entre hilos/procesos concurrentes.

### 3.2 Organización por defecto

Se adopta estrategia simple en ITER-001:

- Organización técnica fija:
  - `code = default`
  - `name = Default Organization`

Esta decisión reduce complejidad inicial y mantiene la ingest operativa.

## 4. Transaccionalidad y manejo de errores

Decisión aplicada:
- **Transacción por item** (`REQUIRES_NEW`), no por batch completo.

Impacto:
- Un item fallido no revierte los demás.
- El response refleja estado individual por lectura.

Logging mínimo:
- `INFO` en duplicados (con `messageId` / `deviceUid`).
- `WARN` en errores de item.

## 5. Seguridad actual

`SecurityConfig` MVP mantiene permitido:
- `GET /actuator/health`
- `GET /api/ping`
- `POST /api/ingest/**`

## 6. Tests implementados

Hay prueba de integración real con PostgreSQL Testcontainers:
- `IngestControllerIntegrationTest`

Caso validado:
- Enviar el mismo batch 2 veces:
  - Primera: `processed=1`
  - Segunda: `duplicates=1`
- Verificación adicional: conteo en `ingest_event` permanece en 1.

## 7. Comandos útiles

```powershell
cd backend
mvn test
mvn spring-boot:run
```

Smoke test rápido (puerto actual en properties: `8081`):

```powershell
Invoke-WebRequest -Method Post -Uri http://localhost:8081/api/ingest/readings/batch -ContentType application/json -Body '{
  "source":"mqtt-gateway",
  "topic":"agro/finca-01/zona-01/sensor/temperatura/telemetry",
  "readings":[{
    "messageId":"m-0001",
    "deviceId":"temp-01",
    "ts":"2026-02-01T12:00:00Z",
    "value":23.4,
    "unit":"C",
    "battery":3.78,
    "rssi":-70
  }]
}'
```

## 8. SQL de verificación operativa

```sql
select id, device_id, message_id, topic, source, received_at, status
from ingest_event
order by received_at desc;

select device_id, message_id, count(*)
from ingest_event
group by device_id, message_id
having count(*) > 1;

select id, code, name from organization;
select id, organization_id, code, name from farm;
select id, farm_id, code, name from zone;
select id, organization_id, device_uid, name from device;
```

## 9. Pendientes (siguiente iteración)

Aún no implementado en esta iteración:
- Persistencia de `TelemetryReading` (ITER-002).
- Dedupe por `sensor_id + message_id` en `telemetry_reading` (ITER-002).
- Update de denormalizados (`lastSeenAt`, `lastRssi`, `lastBatteryV`) en `Device/Sensor` (ITER-003).
- Actualización de estado final en `IngestEvent` (`PROCESSED/ERROR` con `processedAt`) (ITER-003).
- Política de clasificación detallada por tipo de error (`DISCARD` vs `ERROR`) (ITER-004).

## 10. Referencia de implementación

Para detalle completo de lo implementado en esta iteración:
- `docs/impl/ITER-001-ingest-event-dedupe.md`
