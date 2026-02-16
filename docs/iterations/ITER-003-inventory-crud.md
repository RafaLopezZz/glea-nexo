# ITER-003 - Inventory CRUD (Farms, Zones, Devices)

**Fecha:** 15/02/2026  
**Estado:** ✅ Completado

## Resumen

Se implementó el inventario CRUD mínimo para `Farm`, `Zone` y `Device` en backend Spring Boot, con scoping por organización (`X-Org-Code`), errores globales consistentes y trazabilidad por correlation id (`X-Correlation-Id` + MDC).

## Alcance funcional

- Farms:
  - `POST /api/farms`
  - `GET /api/farms`
  - `GET /api/farms/{farmId}`
  - `PUT /api/farms/{farmId}`
  - `DELETE /api/farms/{farmId}`
- Zones:
  - `POST /api/farms/{farmId}/zones`
  - `GET /api/farms/{farmId}/zones`
  - `GET /api/zones/{zoneId}`
  - `PUT /api/zones/{zoneId}`
  - `DELETE /api/zones/{zoneId}`
- Devices:
  - `POST /api/zones/{zoneId}/devices`
  - `GET /api/devices`
  - `GET /api/devices/{deviceId}`
  - `PUT /api/devices/{deviceId}`
  - `DELETE /api/devices/{deviceId}`

## Decisiones técnicas

1. **Org Resolver**: `OrganizationContextResolver` lee `X-Org-Code`; fallback a `default`; 404 si no existe.
2. **Scoping tenant soft**: listados y operaciones por id filtran por `organization_id` resuelta.
3. **No nulos lógicos en inventario**: en create de farm/device se asigna organización explícitamente.
4. **Paginación**: `page/size/sort` con `PageRequest` y filtros `q` opcionales.
5. **Errores globales**: `@ControllerAdvice` unifica 400/404/409/500 con `correlationId`.
6. **MDC**: filtro `OncePerRequestFilter` propaga `X-Correlation-Id` y lo devuelve en respuesta.
7. **OpenAPI 3.0**: controllers inventario documentados con `@Operation/@ApiResponses`; springdoc habilitado.

## Archivos tocados

### Backend

- `backend/pom.xml`
- `backend/src/main/java/com/glea/nexo/domain/repository/FarmRepository.java`
- `backend/src/main/java/com/glea/nexo/domain/repository/ZoneRepository.java`
- `backend/src/main/java/com/glea/nexo/domain/repository/DeviceRepository.java`
- `backend/src/main/java/com/glea/nexo/application/inventory/OrganizationContextResolver.java`
- `backend/src/main/java/com/glea/nexo/application/inventory/PaginationUtils.java`
- `backend/src/main/java/com/glea/nexo/application/inventory/FarmService.java`
- `backend/src/main/java/com/glea/nexo/application/inventory/ZoneService.java`
- `backend/src/main/java/com/glea/nexo/application/inventory/DeviceService.java`
- `backend/src/main/java/com/glea/nexo/api/controller/inventory/FarmController.java`
- `backend/src/main/java/com/glea/nexo/api/controller/inventory/ZoneController.java`
- `backend/src/main/java/com/glea/nexo/api/controller/inventory/DeviceController.java`
- `backend/src/main/java/com/glea/nexo/api/dto/inventory/*`
- `backend/src/main/java/com/glea/nexo/api/error/*`
- `backend/src/main/java/com/glea/nexo/config/web/CorrelationIdFilter.java`
- `backend/src/main/java/com/glea/nexo/config/openapi/OpenApiConfig.java`
- `backend/src/main/resources/logback-spring.xml`
- `backend/src/test/java/com/glea/nexo/api/controller/InventoryCrudIntegrationTest.java`
- `backend/src/test/java/com/glea/nexo/api/controller/IngestControllerIntegrationTest.java`

### Infra

- `infra/compose/docker-compose.platform.yml` (healthcheck backend corregido a `localhost:8081`)

## Validación

- `mvn test` en `backend/`: ✅
  - Ingest tests existentes: ✅
  - Inventory tests nuevos: ✅

## Pendientes

- Endurecer seguridad (JWT + roles) para inventario.
- Estabilizar serialización de páginas (`PageImpl` warning de Spring Data).
- Considerar mover tests de integración de `ddl-auto=create-drop` a estrategia full Flyway cuando exista baseline completo de schema.

## Hotfix de contrato - Device UID inmutable

### Problema detectado

Se identificó como P0 la posibilidad de mutar la identidad técnica (`deviceUid`) por `PUT /api/devices/{deviceId}` en contratos previos.

### Ajuste aplicado

- El DTO de update (`DeviceUpdateRequestDto`) no incluye `deviceUid`.
- `DeviceService.updateDevice(...)` actualiza únicamente campos mutables:
  - `name` (trim; blanco explícito se normaliza a `null`)
  - `state` (si viene informado)
- Se mantiene `deviceUid` solo para creación (`POST /api/zones/{zoneId}/devices`).
- Se reforzó comportamiento con `spring.jackson.deserialization.fail-on-unknown-properties=true` para devolver `400` si llega `deviceUid` en body de update.

### Archivos impactados en hotfix

- `backend/src/main/java/com/glea/nexo/api/controller/inventory/DeviceController.java`
- `backend/src/test/java/com/glea/nexo/api/controller/InventoryCrudIntegrationTest.java`
- `docs/api/inventory-openapi.md`
- `docs/runbook/inventory-crud.md`
- `README.md`

### Validación

- Test de integración: update cambia `name/state` y **no** cambia `device_uid`.
- Test de integración: payload de update con `deviceUid` devuelve `400`.
