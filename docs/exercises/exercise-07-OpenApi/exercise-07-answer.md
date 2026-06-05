# Ejercicio 7 — OpenAPI como contrato vivo

## Objetivo

Convertir Swagger/OpenAPI en un contrato usable sin abrir el código, alineado con el comportamiento real del backend.

## Qué se revisó

Se inventarió la API real expuesta por el backend y se confirmó que incluye:

- `GET /api/ping`
- `POST /api/ingest/readings/batch`
- `GET /api/telemetry/readings`
- `GET /api/telemetry/latest`
- `GET /api/alerts`
- CRUD de inventory para `farms`, `zones` y `devices`

Esto implica que el ejercicio no afecta solo a telemetría, sino a toda la API pública expuesta por los controladores.

## Parámetros críticos identificados

Se documentaron como críticos:

- `X-Org-Code`
- `from` / `to`
- `zoneId` / `deviceId`
- `page` / `size` / `sort`
- `q`
- `farmId` / `zoneId` / `deviceId` en path params
- campos clave del ingest: `source`, `topic`, `messageId`, `deviceId`, `ts`

El objetivo fue dejar clara su semántica, formato, opcionalidad y efecto funcional.

## Mejoras aplicadas en Swagger/OpenAPI

### 1. Header reutilizable `X-Org-Code`

Se añadió como componente global reutilizable en `OpenApiConfig`, documentando que:

- es opcional,
- su default lógico es `default`,
- y puede producir `404` si la organización resuelta no existe.

### 2. Ingest con ejemplos reales

En `IngestController` se reforzó:

- `@Tag(name = "Ingest")`
- descripción del contrato temporal
- ejemplo completo de request batch
- ejemplos de response para:
  - item procesado
  - item duplicado
- respuestas `400`, `404` y `409`

### 3. Observabilidad con semántica visible

En `TelemetryController` y `AlertController` se mejoró la documentación de:

- `zoneId`
- `deviceId`
- `from`
- `to`

y se añadieron ejemplos de response reales para:

- histórico de lecturas
- snapshot latest
- alertas operativas

También se documentaron respuestas `400` y `404` con el error estándar.

### 4. Inventory más explícito

En `FarmController`, `ZoneController` y `DeviceController` se reforzó la documentación de:

- `X-Org-Code`
- paginación (`page`, `size`, `sort`)
- filtros (`q`, `farmId`, `zoneId`, `state`)
- path params (`farmId`, `zoneId`, `deviceId`)

Además, se añadieron ejemplos de campos a los DTOs de request/response para farms, zones y devices.

### 5. Endpoint de sistema visible

`PingController` quedó documentado con:

- `@Tag(name = "System")`
- `@Operation`
- ejemplo de respuesta `ok`

### 6. Modelo de error visible en Swagger

Se añadieron anotaciones `@Schema` a:

- `ApiErrorResponse`
- `ApiFieldError`

para que Swagger muestre mejor la forma del error estándar del backend.

## Archivos modificados

### Configuración OpenAPI

- `backend/src/main/java/com/glea/nexo/config/openapi/OpenApiConfig.java`

### Controllers

- `backend/src/main/java/com/glea/nexo/api/controller/PingController.java`
- `backend/src/main/java/com/glea/nexo/api/controller/IngestController.java`
- `backend/src/main/java/com/glea/nexo/api/controller/TelemetryController.java`
- `backend/src/main/java/com/glea/nexo/api/controller/AlertController.java`
- `backend/src/main/java/com/glea/nexo/api/controller/inventory/FarmController.java`
- `backend/src/main/java/com/glea/nexo/api/controller/inventory/ZoneController.java`
- `backend/src/main/java/com/glea/nexo/api/controller/inventory/DeviceController.java`

### DTOs / errores

- `backend/src/main/java/com/glea/nexo/api/dto/ingest/IngestBatchRequestDto.java`
- `backend/src/main/java/com/glea/nexo/api/dto/ingest/IngestReadingDto.java`
- `backend/src/main/java/com/glea/nexo/api/dto/ingest/IngestBatchResponseDto.java`
- `backend/src/main/java/com/glea/nexo/api/dto/ingest/IngestBatchItemResponseDto.java`
- `backend/src/main/java/com/glea/nexo/api/dto/telemetry/TelemetryReadingResponseDto.java`
- `backend/src/main/java/com/glea/nexo/api/dto/telemetry/TelemetryLatestResponseDto.java`
- `backend/src/main/java/com/glea/nexo/api/dto/alerts/AlertResponseDto.java`
- `backend/src/main/java/com/glea/nexo/api/dto/inventory/FarmCreateRequestDto.java`
- `backend/src/main/java/com/glea/nexo/api/dto/inventory/FarmResponseDto.java`
- `backend/src/main/java/com/glea/nexo/api/dto/inventory/ZoneCreateRequestDto.java`
- `backend/src/main/java/com/glea/nexo/api/dto/inventory/ZoneResponseDto.java`
- `backend/src/main/java/com/glea/nexo/api/dto/inventory/DeviceCreateRequestDto.java`
- `backend/src/main/java/com/glea/nexo/api/dto/inventory/DeviceUpdateRequestDto.java`
- `backend/src/main/java/com/glea/nexo/api/dto/inventory/DeviceResponseDto.java`
- `backend/src/main/java/com/glea/nexo/api/error/ApiErrorResponse.java`
- `backend/src/main/java/com/glea/nexo/api/error/ApiFieldError.java`

## Verificación

Se ejecutó:

```bash
mvn clean -DskipTests compile
mvn test "-Dtest=IngestControllerIntegrationTest,ObservabilityControllerIntegrationTest"
```

Resultado:

- compilación OK
- 12 tests ejecutados
- 0 fallos
- 0 errores
- BUILD SUCCESS

## Conclusión

El Ejercicio 7 queda resuelto a nivel práctico: Swagger/OpenAPI ya no describe solo rutas y nombres de parámetros, sino que refleja el contrato real del backend con ejemplos, restricciones temporales, multi-organización, filtros, paginación y errores estándar.

Con esto, OpenAPI pasa a comportarse como contrato vivo y deja al backend preparado para el siguiente trabajo del Bloque 3: matriz de status codes y endurecimiento documental de errores.
