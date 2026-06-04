# Ejercicio 5 — Endurecer contrato temporal

## Objetivo

Convertir la semántica temporal definida en el Ejercicio 4 en reglas verificables de API, validaciones backend, tests automáticos y documentación OpenAPI.

## Decisiones de contrato adoptadas

### Ingest de telemetría

- `ts` es obligatorio.
- `ts` representa siempre `event time`.
- si falta `ts`, la lectura debe fallar con error controlado.
- no se permite usar `Instant.now()` como sustituto de negocio.

### Consultas históricas

- `from` y `to` se mantienen opcionales.
- si ambos están presentes, debe cumplirse `from <= to`.
- si ambos están presentes, el rango máximo permitido es de 2 años.
- si alguna regla se viola, el endpoint debe responder `400 Bad Request`.

## Cambios esperados en backend

### 1. Endurecer el contrato de entrada

`IngestReadingDto.ts` debe validarse como obligatorio para impedir que la semántica de `TelemetryReading.ts` cambie según el caso.

Consecuencia buscada:

- toda lectura persistida tiene un `event time` real;
- una lectura sin `ts` no entra como telemetría válida.

### 2. Eliminar el fallback temporal ambiguo

El código actual usa:

```java
telemetry.setTs(reading.ts() != null ? reading.ts() : Instant.now());
```

Ese fallback mezcla `event time` con `ingest time`. La implementación correcta debe persistir exclusivamente `reading.ts()` y rechazar el request si `ts` no existe.

### 3. Validar rangos temporales de consulta

Los endpoints que aceptan `from` y `to` deben compartir una regla única:

- si `from > to`, error `400`;
- si `to - from > 2 años`, error `400`.

Esta validación debe aplicarse de forma consistente en telemetría y alertas.

### 4. Mantener separados los tiempos operativos

La regla anterior no elimina otros timestamps. Solo aclara sus responsabilidades:

- `TelemetryReading.ts` = `event time`
- `IngestEvent.receivedAt` = `received time`
- `IngestEvent.processedAt` = `processed time`
- `createdAt` = `persisted time`

## Tests que deben quedar en verde

### Ingest

- lectura válida con `ts` explícito
- lectura sin `ts` → `400`
- lectura con fecha inválida → `400`
- verificación de que `TelemetryReading.ts` conserva el valor del payload

### Consultas

- `from > to` → `400`
- rango exactamente de 2 años → válido
- rango mayor de 2 años → `400`
- mismo comportamiento para `/api/telemetry/*` y `/api/alerts`

## Swagger / OpenAPI

El contrato temporal debe quedar visible sin leer el código.

Se debe documentar:

- que `readings[].ts` es obligatorio y representa `event time`;
- que `from` y `to` aceptan ISO-8601;
- que `from <= to` si ambos están presentes;
- que el rango máximo histórico es de 2 años;
- ejemplos válidos e inválidos;
- respuestas `400` para violaciones del contrato.

## Resultado esperado

Al cerrar el ejercicio, el sistema debe impedir timestamps ambiguos y convertir la semántica temporal en una regla técnica estable:

- ninguna telemetría válida se persiste sin `ts`;
- `TelemetryReading.ts` pasa a ser confiable como timestamp canónico de negocio;
- las consultas históricas quedan limitadas y bien definidas;
- los errores temporales devuelven respuesta controlada;
- el contrato queda respaldado por tests y Swagger.

## Conclusión

El Ejercicio 4 aclaró qué significa el tiempo en Glea Nexo. El Ejercicio 5 obliga al backend a respetar esa semántica. La decisión central es simple: el tiempo de negocio no se inventa. Si no llega `ts`, la lectura es inválida; si el rango consultado es incoherente o excede 2 años, la consulta también lo es.
