# ITER-001 - Persistencia minima + dedupe de IngestEvent

## 1) Resumen de cambios
- Se implemento el flujo real de `POST /api/ingest/readings/batch` con persistencia en BD.
- Se agrego `IngestServiceImpl` que procesa el batch item por item y construye respuesta itemizada.
- Se agrego `IngestItemProcessor` con transaccion `REQUIRES_NEW` por item para aislar fallos.
- Se implemento resolucion/creacion de inventario basico:
  - `Organization` por defecto (`code=default`)
  - `Farm` por `organization+code`
  - `Zone` por `farm+code`
  - `Device` por `organization+deviceUid`
- Se persiste `IngestEvent` con dedupe en dos capas:
  - pre-check con `existsByDevice_IdAndMessageId`
  - guardian final por UNIQUE `uk_ingest_device_message` capturando `DataIntegrityViolationException`
- Se agrego test de integracion con PostgreSQL Testcontainers:
  - mismo batch enviado 2 veces => primera `PROCESSED`, segunda `DUPLICATE`.

## 2) Archivos tocados (lista)
- `backend/pom.xml`
- `backend/src/main/java/com/glea/nexo/api/controller/IngestController.java`
- `backend/src/main/java/com/glea/nexo/api/dto/ingest/IngestBatchRequestDto.java`
- `backend/src/main/java/com/glea/nexo/api/dto/ingest/IngestReadingDto.java`
- `backend/src/main/java/com/glea/nexo/api/dto/ingest/IngestBatchResponseDto.java`
- `backend/src/main/java/com/glea/nexo/api/dto/ingest/IngestBatchItemResponseDto.java`
- `backend/src/main/java/com/glea/nexo/application/ingest/IngestService.java`
- `backend/src/main/java/com/glea/nexo/application/ingest/IngestServiceImpl.java`
- `backend/src/main/java/com/glea/nexo/application/ingest/IngestItemProcessor.java`
- `backend/src/main/java/com/glea/nexo/application/ingest/TopicParser.java`
- `backend/src/main/java/com/glea/nexo/domain/ingest/IngestEvent.java`
- `backend/src/main/java/com/glea/nexo/domain/repository/OrganizationRepository.java`
- `backend/src/main/java/com/glea/nexo/domain/repository/FarmRepository.java`
- `backend/src/main/java/com/glea/nexo/domain/repository/ZoneRepository.java`
- `backend/src/main/java/com/glea/nexo/domain/repository/DeviceRepository.java`
- `backend/src/test/java/com/glea/nexo/api/controller/IngestControllerIntegrationTest.java`

## 3) Decisiones tecnicas y por que
- Transaccion por item (`REQUIRES_NEW`):
  - Permite que un item en error no tumbe todo el batch.
  - Cumple robustez para respuesta detallada por reading.
- Estrategia de organizacion por defecto:
  - Se usa una sola organizacion tecnica `default` (`code=default`, `name=Default Organization`).
  - Minimiza complejidad en esta iteracion sin bloquear ingest.
- Dedupe en dos capas:
  - `existsBy...` reduce inserts innecesarios.
  - UNIQUE en DB es el guardia final contra condiciones de carrera.
- Parse topic:
  - Si el reading no trae `topic`, se usa `request.topic`.
  - Si ninguno trae topic, el item se marca `ERROR`.

## 4) Flujo paso a paso (como funciona ahora)
1. Controller recibe `IngestBatchRequestDto` y delega al servicio.
2. Servicio valida defensivamente que `readings` no este vacio.
3. Itera cada reading:
   - Ejecuta `IngestItemProcessor.process(...)` en transaccion nueva.
4. En cada item:
   - Resuelve topic (`reading.topic` -> `request.topic`).
   - Parsea farm/zone/tipo con `TopicParser`.
   - Resuelve device (`topic` si viene, si no `reading.deviceId`).
   - Resuelve/crea `Organization/Farm/Zone/Device`.
   - Revisa duplicado por `existsByDevice_IdAndMessageId`.
   - Intenta insertar `IngestEvent`.
   - Si salta integridad por UNIQUE => `DUPLICATE`.
5. Servicio acumula contadores (`processed`, `duplicates`, `errors`) y `items[]`.
6. Devuelve `IngestBatchResponseDto` itemizado.

## 5) SQL/verificacion (queries utiles)
```sql
-- Ver eventos ingestidos
select id, device_id, message_id, topic, source, received_at, status
from ingest_event
order by received_at desc;

-- Ver constraint de dedupe
select conname, conrelid::regclass as table_name
from pg_constraint
where conname = 'uk_ingest_device_message';

-- Confirmar que no hay duplicados por (device_id, message_id)
select device_id, message_id, count(*) as total
from ingest_event
group by device_id, message_id
having count(*) > 1;

-- Ver inventario minimo creado por ingest
select id, code, name from organization;
select id, organization_id, code, name from farm;
select id, farm_id, code, name from zone;
select id, organization_id, device_uid, name from device;
```

## 6) Como ejecutar tests (comandos)
```powershell
cd backend
mvn test
```

## 7) Checklist DoD (Done)
- [x] Compila (`mvn test` compila + ejecuta pruebas).
- [x] Persistencia real de `IngestEvent`.
- [x] Resolve/create de location + inventory basico.
- [x] Dedupe por pre-check + UNIQUE constraint capturada.
- [x] Respuesta de batch con contadores e items.
- [x] Test de integracion para doble envio del mismo batch.
- [x] Logging basico en duplicados y errores.

## 8) Riesgos/pendientes (TODO)
- TODO: Estandarizar politica de errores por tipo (`DISCARD` vs `ERROR`) en iteraciones siguientes.
- TODO: En esta iteracion el `status` de `IngestEvent` queda en valor por defecto (`RECEIVED`); transicion a `PROCESSED/ERROR` queda para ITER-003.
- TODO: Validar y documentar formato estricto de topic para todas las variantes MQTT del proyecto.
- TODO: Ajustar `spring.profiles.active=${MYENV:prod}` para entorno local/test si se requiere un profile dedicado.
