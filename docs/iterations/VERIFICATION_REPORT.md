# VERIFICATION REPORT - ITER-002

**Proyecto:** Glea Nexo  
**Fecha:** 15/02/2026  
**Autor:** Glea Nexo Team (verificación técnica)

***

## 1. Summary

**Resultado global:** ⚠️ **Condicional**  
La implementación de ITER-002 está correcta a nivel de código y tests de integración (pass), pero el backend actualmente desplegado por Docker no está alineado con ese código (runtime desfasado), por lo que la validación E2E en entorno levantado no cumple el objetivo funcional completo.

## 2. Checklist Results

### A. Repositorios

| Check | Estado | Evidencia |
|---|---|---|
| `SensorRepository` existe y firma correcta | ✅ | `backend/src/main/java/com/glea/nexo/domain/repository/SensorRepository.java` |
| `SensorTypeRepository` existe y firma correcta | ✅ | `backend/src/main/java/com/glea/nexo/domain/repository/SensorTypeRepository.java` |
| `UnitRepository` existe y firma correcta | ✅ | `backend/src/main/java/com/glea/nexo/domain/repository/UnitRepository.java` |
| `TelemetryReadingRepository` existe y firma correcta | ✅ | `backend/src/main/java/com/glea/nexo/domain/repository/TelemetryReadingRepository.java` |
| `@Repository` presente en los 4 | ⚠️ | Falta en `backend/src/main/java/com/glea/nexo/domain/repository/TelemetryReadingRepository.java:11` |
| Naming JPA correcto | ✅ | Métodos `existsBySensor_IdAndMessageId`, `findByCode`, etc. |
| Dependencias circulares | ✅ | No se observaron en revisión estática |

### B. Entidades JPA

| Check | Estado | Evidencia |
|---|---|---|
| Setters requeridos en `TelemetryReading` | ✅ | `backend/src/main/java/com/glea/nexo/domain/ingest/TelemetryReading.java` |
| Setters/getters requeridos en `Sensor` | ✅ | `backend/src/main/java/com/glea/nexo/domain/inventory/Sensor.java` |
| Setters requeridos en `IngestEvent` | ✅ | `backend/src/main/java/com/glea/nexo/domain/ingest/IngestEvent.java` |
| Tipos y anotaciones JPA correctas | ✅ | `BigDecimal`, `Instant`, `@ManyToOne`, `@Column`, `@Enumerated` |

### C. IngestItemProcessor

| Check | Estado | Evidencia |
|---|---|---|
| 9 fases implementadas | ✅ | `backend/src/main/java/com/glea/nexo/application/ingest/IngestItemProcessor.java` |
| Helper methods presentes | ✅ | Líneas aprox. `343`, `358`, `369`, `394` |
| Logging INFO/ERROR en puntos clave | ✅ | `IngestItemProcessor.java:93`, `188` |
| Manejo `DataIntegrityViolationException` | ✅ | Catch dedicado en `process()` |
| `IngestEvent` se marca `PROCESSED` | ✅ | `IngestItemProcessor.java:184` |
| `IngestEvent` se marca error en excepciones | ✅ | Catch general de `Exception` |
| Estado consistente en duplicado por constraint | ⚠️ | En catch de `DataIntegrityViolationException` retorna `DUPLICATE` sin actualizar estado del `ingest_event` existente |

### D. Tests de Integración

| Check | Estado | Evidencia |
|---|---|---|
| Testcontainers PostgreSQL | ✅ | `IngestControllerIntegrationTest.java:46` |
| `shouldPersistTelemetryReadingAndUpdateSensor` | ✅ | `IngestControllerIntegrationTest.java:127` |
| `shouldDetectDuplicateTelemetry` | ✅ | `IngestControllerIntegrationTest.java:188` |
| Assertions de BD (no solo HTTP) | ✅ | Validaciones de repositorios y entidades |
| Ejecución real | ✅ | `mvn -Dtest=IngestControllerIntegrationTest test` -> 3 tests OK |

### E. Base de Datos

| Check | Estado | Evidencia |
|---|---|---|
| Catálogo `sensor_type` esperado 8 | ⚠️ | En entorno Docker actual hay 9 (`TEST` adicional) |
| Catálogo `unit` esperado 9 | ✅ | Query directa: 9 |
| `uk_reading_sensor_message` | ✅ | Query a `pg_constraint` confirma UNIQUE `(sensor_id, message_id)` |
| Índices `idx_tr_sensor_ts`, `idx_tr_zone_ts`, `idx_tr_device_ts` | ✅ | Query `pg_indexes` confirma los 3 |

### F. Funcionalidad End-to-End (entorno Docker activo)

| Check | Estado | Evidencia |
|---|---|---|
| API responde 200 y dedupe | ✅ | `processed=1` primera vez, luego `duplicates=1` con mismo `messageId` |
| Persiste `TelemetryReading` | ❌ | Tabla `telemetry_reading` permanece vacía |
| Actualiza `Sensor.state=ONLINE` | ❌ | No hay sensor derivado de lecturas en runtime actual |
| `IngestEvent.status=PROCESSED` | ⚠️ | Se persiste `ingest_event`, pero detalle de respuesta dice `ingest event persisted` (sin flujo completo) |
| Latencia `<100ms` | ⚠️ | No se pudo certificar confiablemente por desalineación de build/runtime |

### G. Manejo de Errores (entorno Docker activo)

| Check | Estado | Evidencia |
|---|---|---|
| Topic con tipo inválido debe fallar claro | ❌ | Runtime actual devuelve `PROCESSED` para tipo inventado |
| `ts = null` fallback razonable | ⚠️ | Runtime actual procesa, pero no genera `telemetry_reading` |
| Auto-provisioning device/sensor | ⚠️ | Runtime actual procesa, pero sin trazas de persistencia de sensor/telemetría |

## 3. Issues Found (orden severidad)

### Critical

1. **Entorno Docker ejecuta backend desfasado respecto a ITER-002**  
   Evidencia: respuesta item detail `"ingest event persisted"`, logs sin `"Telemetry persisted"`, y `telemetry_reading` vacío tras POST.  
   Impacto: bloquea validación E2E real de ITER-002.  
   Referencias: `backend/src/main/java/com/glea/nexo/application/ingest/IngestItemProcessor.java:188` (log esperado), logs contenedor backend.

2. **Healthcheck interno del contenedor backend apunta a puerto incorrecto**  
   `server.port=8081` pero healthcheck usa `localhost:8080`.  
   Impacto: servicio reportado `unhealthy` aunque endpoints externos funcionan.  
   Referencias: `infra/compose/docker-compose.platform.yml:37`, `backend/src/main/resources/application.properties:11`.

### Medium

3. **Falta `@Repository` en `TelemetryReadingRepository`**  
   Spring Data lo detecta por escaneo, pero no cumple checklist explícito.  
   Referencia: `backend/src/main/java/com/glea/nexo/domain/repository/TelemetryReadingRepository.java:11`.

4. **Catálogo `sensor_type` contaminado en entorno local (9 en vez de 8)**  
   Hay un registro extra `TEST`.  
   Impacto: validaciones por conteo fallan en runbooks estrictos.

## 4. Metrics

| Métrica | Valor |
|---|---|
| Tests integración ejecutados | 3 |
| Tests integración OK | 3 |
| Tests fallidos | 0 |
| Hallazgos críticos | 2 |
| Hallazgos medios | 2 |
| Cobertura tests | N/D (no JaCoCo configurado) |

## 5. Recommendations

1. Rebuild y redeploy del backend antes de cerrar ITER-002 E2E:  
   `docker compose -f infra/compose/docker-compose.platform.yml build backend`  
   `docker compose -f infra/compose/docker-compose.platform.yml up -d backend`
2. Corregir healthcheck interno a `http://localhost:8081/actuator/health` en `docker-compose.platform.yml`.
3. Añadir `@Repository` en `TelemetryReadingRepository` para cumplir estándar interno.
4. Limpiar catálogo local (`sensor_type` extra `TEST`) o documentar como dato de prueba no productivo.
5. Re-ejecutar script E2E final tras redeploy y adjuntar evidencia de `telemetry_reading` + `sensor` + `ingest_event`.

***

## 6. Evidencia de ejecución

- Comando ejecutado: `mvn -Dtest=IngestControllerIntegrationTest test`
- Resultado: `BUILD SUCCESS` con `Tests run: 3, Failures: 0, Errors: 0`
- Compose status observado: backend `unhealthy` por healthcheck incorrecto, aunque `GET /actuator/health` y `GET /api/ping` responden.
