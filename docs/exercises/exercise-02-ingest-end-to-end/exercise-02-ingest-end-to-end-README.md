# Ejercicio 2 — Validar ingest normal end-to-end

## Veredicto

**Estado:** REAL  
**Fecha de validación:** 2026-06-01  
**Rama:** `exercise/02-ingest-end-to-end`

El ejercicio valida que una lectura publicada desde el edge en MQTT puede recorrer el flujo completo:

```text
Raspberry Pi
  → Mosquitto
  → Node-RED Bridge
  → Backend Spring Boot /api/ingest/readings/batch
  → PostgreSQL ingest_event
  → PostgreSQL telemetry_reading
  → API /api/telemetry/latest
```

Durante la validación se detectó y corrigió un problema real en el bridge Node-RED: el batch se estaba construyendo con `messageId=undefined` porque el nodo Function asumía que `msg.payload` ya llegaba como objeto JSON.

---

## Objetivo

Demostrar con evidencia que una lectura generada en el edge:

1. se publica correctamente en MQTT;
2. es recibida por Mosquitto;
3. es transformada por Node-RED;
4. llega al backend mediante `POST /api/ingest/readings/batch`;
5. queda registrada en `ingest_event`;
6. queda persistida en `telemetry_reading`;
7. aparece en los endpoints de observabilidad.

---

## Arquitectura validada

```text
Equipo local 192.168.1.10
├─ Backend Spring Boot      : http://localhost:8080
├─ Frontend Angular         : http://localhost:4200
└─ PostgreSQL               : glea-postgres / puerto host 3609

Raspberry Pi 192.168.1.248
├─ Mosquitto                : puerto 1883
├─ Node-RED                 : http://192.168.1.248:1880
└─ edge-python simulator    : publica telemetría en agro/#
```

Variable validada en Node-RED:

```bash
docker compose -f infra/compose/docker-compose.edge.yml exec nodered sh -c 'env | grep BACKEND'
```

Resultado esperado:

```text
BACKEND_URL=http://192.168.1.10:8080
```

---

## Alcance

### Entra

- Publicación MQTT manual con `messageId` conocido.
- Validación de transformación Node-RED.
- Validación de persistencia en `ingest_event`.
- Validación de persistencia en `telemetry_reading`.
- Validación de exposición en `/api/telemetry/latest`.
- Documentación del fallo `messageId=undefined`.
- Corrección del mapping Node-RED.

### No entra

- Offline/replay.
- Dedupe avanzado.
- JWT/API security.
- Refactor del Dockerfile de `edge-python`.
- WebSockets/SSE.
- Rediseño del contrato REST.
- Cambios frontend, salvo posible captura o evidencia.

---

## Lectura MQTT de prueba inicial

Se publicó manualmente una lectura en el topic:

```text
agro/finca1/zona1/pi-gw-001/sensor/exercise02-temp-01/TEMPERATURE/telemetry
```

Payload inicial:

```json
{
  "deviceId": "pi-gw-001",
  "sensorId": "exercise02-temp-01",
  "type": "temperature",
  "ts": "2026-06-01T17:02:02Z",
  "value": 23.7,
  "unit": "C",
  "battery": 3.95,
  "quality": "good",
  "messageId": "ex02-20260601170202"
}
```

Evidencia MQTT:

```text
agro/finca1/zona1/pi-gw-001/sensor/exercise02-temp-01/TEMPERATURE/telemetry {"deviceId":"pi-gw-001","sensorId":"exercise02-temp-01","type":"temperature","ts":"2026-06-01T17:02:02Z","value":23.7,"unit":"C","battery":3.95,"quality":"good","messageId":"ex02-20260601170202"}
```

Resultado inicial:

```text
MQTT publish/manual : OK
Mosquitto recibe    : OK
ingest_event        : 0 rows
telemetry_reading   : 0 rows
```

---

## Hallazgo 1 — La lectura MQTT no se persistía

Al buscar el `messageId` inicial en PostgreSQL:

```sql
select id, device_id, message_id, topic, source, status, received_at, processed_at
from ingest_event
where message_id = 'ex02-20260601170202';
```

Resultado:

```text
(0 rows)
```

Y en `telemetry_reading`:

```sql
select *
from telemetry_reading
where message_id = 'ex02-20260601170202';
```

Resultado:

```text
(0 rows)
```

Conclusión inicial:

```text
La publicación MQTT funcionaba, pero la lectura no llegaba correctamente a persistencia.
```

---

## Hallazgo 2 — Backend recibía `messageId=undefined`

Los logs del backend mostraban repetidamente:

```text
ingest duplicate pre-check messageId=undefined gatewayUid=pi-gw-001
```

Esto indica que Node-RED sí estaba enviando peticiones al backend, pero el batch construido tenía un `messageId` incorrecto.

Flujo observado:

```text
MQTT payload contiene messageId correcto
  → Node-RED transforma payload
  → Backend recibe messageId=undefined
  → Dedupe detecta duplicado de undefined
```

---

## Hallazgo 3 — Contrato backend estricto

También se validó mediante POST directo que el backend rechaza campos no soportados por `IngestReadingDto`.

El intento de enviar `sensorId` dentro de cada reading produjo:

```http
HTTP/1.1 400
```

Error observado:

```text
JSON parse error: Unrecognized field "sensorId"
(class com.glea.nexo.api.dto.ingest.IngestReadingDto), not marked as ignorable
```

Conclusión:

```text
El payload MQTT bruto puede contener sensorId, type y quality,
pero Node-RED debe mapearlo a un DTO compatible con el backend.
```

Contrato compatible hacia backend:

```json
{
  "source": "edge-nodered",
  "topic": null,
  "readings": [
    {
      "messageId": "...",
      "deviceId": "pi-gw-001",
      "topic": "agro/finca1/zona1/pi-gw-001/sensor/exercise02-temp-01/TEMPERATURE/telemetry",
      "ts": "2026-06-01T17:27:40Z",
      "value": 23.7,
      "unit": "C",
      "battery": 3.95,
      "rssi": null,
      "rawPayload": { }
    }
  ]
}
```

---

## Causa raíz

El nodo Function de Node-RED `Filter + map + wrap batch` asumía que `msg.payload` ya era un objeto JSON.

Fragmento problemático conceptual:

```javascript
const p = msg.payload || {};

msg.payload = {
  source: 'edge-nodered',
  topic: null,
  readings: [{
    messageId: String(p.messageId),
    deviceId: String(p.deviceId || gw),
    // ...
  }]
};
```

Si `msg.payload` llegaba como string o Buffer, `p.messageId` era `undefined`.

Resultado:

```javascript
String(p.messageId) === "undefined"
```

---

## Fix aplicado en Node-RED

Se endureció el nodo `Filter + map + wrap batch` para:

- parsear `msg.payload` si llega como Buffer;
- parsear `msg.payload` si llega como string JSON;
- validar `messageId`, `ts` y `value`;
- construir un batch compatible con `IngestReadingDto`;
- usar `BACKEND_URL` mediante `msg.url`;
- dejar log de preparación del batch;
- evitar depender de una URL hardcodeada.

Función aplicada:

```javascript
// 1) Solo telemetry
const topic = String(msg.topic || '');
if (!topic.endsWith('/telemetry')) return null;

// 2) Parse robusto del payload MQTT
let p = msg.payload;

if (Buffer.isBuffer(p)) {
  p = p.toString('utf8');
}

if (typeof p === 'string') {
  try {
    p = JSON.parse(p);
  } catch (err) {
    node.error(`Invalid JSON payload: ${err.message}`, msg);
    return null;
  }
}

if (!p || typeof p !== 'object') {
  node.error('Payload is not an object after JSON parsing', msg);
  return null;
}

// 3) Parse del topic MQTT v2 real:
// agro/{finca}/{zona}/{gw}/sensor/{sensorUid}/{sensorType}/telemetry
const parts = topic.split('/');
const sensorIdx = parts.indexOf('sensor');

if (
  parts.length < 8 ||
  parts[0] !== 'agro' ||
  sensorIdx < 0 ||
  sensorIdx + 2 >= parts.length ||
  parts[parts.length - 1] !== 'telemetry'
) {
  node.warn(`Topic unexpected (need v2): ${topic}`);
  return null;
}

const finca = parts[1];
const zona = parts[2];
const gw = parts[3];
const sensorUid = parts[sensorIdx + 1];
const sensorType = parts[sensorIdx + 2];

if (!gw || !sensorUid || !sensorType) {
  node.warn(`Missing gw/sensorUid/sensorType in topic: ${topic}`);
  return null;
}

// 4) Validaciones mínimas de contrato
if (!p.messageId) {
  node.error(`Missing messageId in payload. topic=${topic} payload=${JSON.stringify(p)}`, msg);
  return null;
}

if (!p.ts) {
  node.error(`Missing ts in payload. messageId=${p.messageId}`, msg);
  return null;
}

if (p.value === undefined || p.value === null) {
  node.error(`Missing value in payload. messageId=${p.messageId}`, msg);
  return null;
}

// 5) Normaliza unidades del simulador a códigos backend
const u = (p.unit == null) ? null : String(p.unit);
const unitMap = {
  'C': 'C',
  '%RH': 'PERCENT',
  '%VWC': 'PERCENT',
  'mS/cm': 'MS_CM',
  'pH': 'PH_SCALE',
  'lux': 'LUX',
  'hPa': 'HPA',
  'V': 'VOLT',
  'm/s': 'M_PER_S',
  'mm': 'MM'
};
const unitCode = u ? (unitMap[u] || u.toUpperCase()) : null;

// 6) URL dinámica desde variable de entorno
const baseUrl = env.get('BACKEND_URL') || 'http://192.168.1.10:8080';

msg.method = 'POST';
msg.url = baseUrl + '/api/ingest/readings/batch';
msg.headers = { 'Content-Type': 'application/json' };

// 7) Body compatible con IngestReadingDto
msg.payload = {
  source: 'edge-nodered',
  topic: null,
  readings: [
    {
      messageId: String(p.messageId),
      deviceId: String(p.deviceId || gw),
      topic: topic,
      ts: String(p.ts),
      value: Number(p.value),
      unit: unitCode,
      battery: (p.battery != null) ? Number(p.battery) : null,
      rssi: (p.rssi != null) ? Number(p.rssi) : null,
      rawPayload: p
    }
  ]
};

node.warn(`Prepared ingest batch messageId=${p.messageId} topic=${topic}`);

return msg;
```

---

## Validación del fix en runtime

Tras desplegar en Node-RED, se verificó que el runtime contiene el fix:

```bash
cd ~/glea-nexo

grep -R "Prepared ingest batch\|env.get('BACKEND_URL')\|Missing messageId" \
  -n edge/nodered/data/flows.json edge/nodered/flows/flows.json
```

Resultado:

```text
edge/nodered/data/flows.json:541: "Prepared ingest batch..."
```

Nota:

```text
El fix apareció primero en edge/nodered/data/flows.json porque se editó desde la UI de Node-RED.
Después debe copiarse a edge/nodered/flows/flows.json para versionarlo.
```

---

## Lectura validada después del fix

Lectura de prueba:

```text
messageId = ex02-mqtt-valid-20260601172740
topic     = agro/finca1/zona1/pi-gw-001/sensor/exercise02-temp-01/TEMPERATURE/telemetry
value     = 23.7
unit      = C
battery   = 3.95
```

---

## Evidencia SQL — `ingest_event`

Consulta:

```sql
select id, device_id, message_id, topic, source, status, received_at, processed_at
from ingest_event
where message_id = 'ex02-mqtt-valid-20260601172740';
```

Resultado:

```text
                  id                  |              device_id               |           message_id           |                                    topic                                    |    source    |  status   |          received_at          |         processed_at
--------------------------------------+--------------------------------------+--------------------------------+-----------------------------------------------------------------------------+--------------+-----------+-------------------------------+-------------------------------
 a9c1bbac-1990-4fca-a589-c282fecd53c1 | e7ddfd32-8c1a-49c8-880d-bf95c3214e32 | ex02-mqtt-valid-20260601172740 | agro/finca1/zona1/pi-gw-001/sensor/exercise02-temp-01/TEMPERATURE/telemetry | edge-nodered | PROCESSED | 2026-06-01 17:27:41.000267+00 | 2026-06-01 17:27:41.008883+00
(1 row)
```

Conclusión:

```text
La lectura llegó al backend y fue procesada correctamente.
```

---

## Evidencia SQL — `telemetry_reading`

Consulta:

```sql
select id, message_id, ts, value_num, batteryv, created_at
from telemetry_reading
where message_id = 'ex02-mqtt-valid-20260601172740';
```

Resultado:

```text
                  id                  |           message_id           |           ts           | value_num | batteryv |          created_at
--------------------------------------+--------------------------------+------------------------+-----------+----------+-------------------------------
 bc1b4054-54a7-47b4-8b3f-42f7a1f387f9 | ex02-mqtt-valid-20260601172740 | 2026-06-01 17:27:40+00 | 23.700000 |     3.95 | 2026-06-01 17:27:41.007956+00
(1 row)
```

Conclusión:

```text
La lectura quedó persistida como telemetría consultable.
```

---

## Evidencia API — `/api/telemetry/latest`

Consulta:

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/telemetry/latest" |
  ConvertTo-Json -Depth 10 |
  Tee-Object -FilePath .\docs\exercises\exercise-02-ingest-end-to-end\evidence\07-api-latest-after-fix.json
```

Resultado relevante:

```json
{
  "zoneId": "ec9cb7a7-b409-4090-8184-142d673d6170",
  "deviceId": "e7ddfd32-8c1a-49c8-880d-bf95c3214e32",
  "deviceUid": "pi-gw-001",
  "sensorId": "443d853b-2ac1-4d71-bbab-21367b1d704f",
  "sensorUid": "exercise02-temp-01",
  "sensorType": "TEMPERATURE",
  "lastTs": "2026-06-01T17:27:40Z",
  "value": 23.7,
  "unit": "C",
  "quality": "UNKNOWN"
}
```

Búsqueda:

```powershell
Get-Content .\docs\exercises\exercise-02-ingest-end-to-end\evidence\07-api-latest-after-fix.json |
  Select-String "exercise02-temp-01|$MSG_ID"
```

Resultado:

```text
"sensorUid": "exercise02-temp-01"
```

Conclusión:

```text
La lectura aparece en la API de latest.
```

---

## Observación — `deviceId` en `/api/telemetry/readings` es UUID interno

Durante la validación de `/api/telemetry/readings` se intentó consultar:

```http
GET /api/telemetry/readings?deviceId=pi-gw-001
```

La API respondió:

```json
{
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "Method parameter 'deviceId': Failed to convert value of type 'java.lang.String' to required type 'java.util.UUID'; Invalid UUID string: pi-gw-001",
  "path": "/api/telemetry/readings",
  "correlationId": "7fe1f89d-3a2e-4c2f-968c-fa1adb61229c"
}
```

Conclusión:

```text
El contrato actual de /api/telemetry/readings espera deviceId como UUID interno,
no como deviceUid funcional.
```

Valor correcto inferido desde `/api/telemetry/latest`:

```text
deviceUid = pi-gw-001
deviceId  = e7ddfd32-8c1a-49c8-880d-bf95c3214e32
```

Consulta correcta:

```http
GET /api/telemetry/readings?deviceId=e7ddfd32-8c1a-49c8-880d-bf95c3214e32
```

Mejora futura:

```text
Evaluar si conviene añadir un filtro deviceUid para facilitar consultas manuales, frontend o edge tooling.
```

---

## Evidencias guardadas

Estructura recomendada:

```text
docs/exercises/exercise-02-ingest-end-to-end/
├─ README.md
└─ evidence/
   ├─ 01-rpi-mqtt-published.txt
   ├─ 02-rpi-nodered-logs.txt
   ├─ 03-backend-filtered.txt
   ├─ 04-sql-ingest-event.txt
   ├─ 05-sql-telemetry-reading.txt
   ├─ 04-sql-ingest-event-after-fix.txt
   ├─ 05-sql-telemetry-reading-after-fix.txt
   ├─ 06-api-readings-device-after-fix.json
   └─ 07-api-latest-after-fix.json
```

---

## Criterio de cierre

| Criterio | Estado |
|---|---|
| Lectura publicada en MQTT con `messageId` conocido | OK |
| Lectura visible en Mosquitto | OK |
| Node-RED prepara batch válido | OK |
| Backend recibe lectura | OK |
| `ingest_event` contiene fila con `status=PROCESSED` | OK |
| `telemetry_reading` contiene fila con valor y timestamp correctos | OK |
| `/api/telemetry/latest` muestra el sensor validado | OK |
| Error inicial documentado | OK |
| Fix Node-RED documentado | OK |
| Limitación de `/readings?deviceId` documentada | OK |
```

---

## Decisión técnica

### Problema

La ingesta parecía activa, pero las lecturas nuevas no se podían rastrear por `messageId`. El backend recibía múltiples lecturas con `messageId=undefined`, provocando deduplicación incorrecta.

### Decisión

Endurecer el nodo Node-RED `Filter + map + wrap batch` para parsear el payload MQTT de forma robusta y validar campos obligatorios antes de enviar al backend.

### Tradeoff

La validación en Node-RED añade lógica al edge, pero evita enviar payloads malformados al backend y facilita el diagnóstico temprano.

### Riesgo

Si el contrato backend evoluciona, el mapping de Node-RED puede quedar desalineado.

### Mitigación

Mantener ejemplos de contrato en OpenAPI, añadir tests/manual runbooks de ingest y documentar claramente qué campos acepta `IngestReadingDto`.

---

## Limitaciones conocidas

1. El nodo HTTP debe quedar configurado para usar `msg.url`, no una URL hardcodeada.
2. El fix aplicado desde la UI vive primero en `edge/nodered/data/flows.json`; debe copiarse a `edge/nodered/flows/flows.json` para versionarlo.
3. `/api/telemetry/readings?deviceId=...` espera UUID interno, no `deviceUid`.
4. `rawPayload` se conserva como trazabilidad, pero no sustituye a una política formal de auditoría.
5. La instalación runtime de dependencias en `edge-python` sigue siendo deuda técnica del Ejercicio 1.

---

## Acciones de versionado pendientes

En Raspberry Pi:

```bash
cd ~/glea-nexo
cp edge/nodered/data/flows.json edge/nodered/flows/flows.json
git diff -- edge/nodered/flows/flows.json
```

En equipo local:

```powershell
cd D:\2026\glea-nexo

scp rafalopezzz@192.168.1.248:/home/rafalopezzz/glea-nexo/edge/nodered/flows/flows.json `
  .\edge\nodered\flows\flows.json

git diff -- .\edge\nodered\flows\flows.json
```

---

## Commits recomendados

Como el ejercicio detectó y corrigió un bug real, se recomiendan dos commits pequeños:

```powershell
git add edge/nodered/flows/flows.json
git commit -m "fix(edge): harden Node-RED ingest batch mapping"
```

Después:

```powershell
git add docs/exercises/exercise-02-ingest-end-to-end
git commit -m "docs(edge): validate ingest end-to-end"
```

Si se prefiere un único commit por ejercicio:

```powershell
git add edge/nodered/flows/flows.json
git add docs/exercises/exercise-02-ingest-end-to-end
git commit -m "fix(edge): validate ingest end-to-end"
```

---

## Próximo ejercicio recomendado

**Ejercicio 3 — Offline y replay controlado**

Objetivo siguiente:

```text
Validar que, ante caída del backend o pérdida de conectividad,
el edge conserva lecturas localmente y puede reenviarlas sin duplicar cuando el backend vuelve.
```
