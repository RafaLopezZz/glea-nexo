# Ejercicio 5 — Resumen final

## 1. Objetivo del ejercicio

Endurecer el contrato temporal de ingest y consulta histórica para que la semántica del tiempo quede definida como regla técnica verificable en API, validaciones backend, tests automáticos y documentación OpenAPI.

## 2. Decisiones adoptadas

### Ingest de telemetría

- `ts` es obligatorio.
- `ts` representa siempre `event time`.
- Si falta `ts`, la API responde con error controlado.
- No se usa `Instant.now()` como fallback de negocio.

### Consultas históricas

- `from` y `to` permanecen opcionales.
- Si ambos están presentes, se valida `from <= to`.
- El rango máximo permitido es de 2 años.
- Toda violación del contrato responde `400 Bad Request`.

## 3. Implementación realizada

Se dejó aplicado el endurecimiento del contrato temporal en los puntos clave del backend:

- `backend/src/main/java/com/glea/nexo/api/dto/ingest/IngestReadingDto.java`
- `backend/src/main/java/com/glea/nexo/application/ingest/IngestItemProcessor.java`
- `backend/src/main/java/com/glea/nexo/application/common/TimeRangeValidator.java`
- `backend/src/main/java/com/glea/nexo/application/telemetry/TelemetryQueryService.java`
- `backend/src/main/java/com/glea/nexo/application/alerts/AlertQueryService.java`
- `backend/src/main/java/com/glea/nexo/api/controller/IngestController.java`
- `backend/src/main/java/com/glea/nexo/api/controller/TelemetryController.java`
- `backend/src/main/java/com/glea/nexo/api/controller/AlertController.java`
- `backend/src/main/java/com/glea/nexo/config/openapi/OpenApiConfig.java`
- `backend/src/main/java/com/glea/nexo/domain/repository/DeviceAlertRepository.java`.

Resumen de la implementación:

- el DTO de ingest exige `ts`;
- el procesamiento de lecturas persiste exclusivamente el `event time` recibido;
- se eliminó el fallback de negocio basado en `Instant.now()`;
- telemetría y alertas comparten una validación temporal consistente mediante `TimeRangeValidator`;
- controladores y OpenAPI exponen el contrato endurecido y sus errores esperados.

## 4. Tests añadidos o ajustados

Quedaron cubiertos o ajustados los siguientes casos:

- rechazo de lecturas sin `ts`;
- rechazo de `ts` mal formado;
- conservación de `event time` en `TelemetryReading.ts`;
- validación de `from > to`;
- validación de rango mayor a 2 años;
- aceptación de rango exactamente de 2 años;
- consistencia de comportamiento entre alerts y telemetry.

## 5. Evidencia de verificación

Comando ejecutado:

```bash
mvn test "-Dtest=IngestControllerIntegrationTest,ObservabilityControllerIntegrationTest"
```

Resultado final:

- 12 tests ejecutados
- 0 fallos
- 0 errores
- `BUILD SUCCESS`

## 6. Conclusión final

`TelemetryReading.ts` queda establecido como timestamp canónico de negocio.

Los campos `receivedAt`, `processedAt` y `createdAt` conservan su responsabilidad operativa y técnica, sin mezclarse con el significado funcional del tiempo del evento.
