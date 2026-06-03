# Ejercicio 24 — Backoff real en Node-RED

**Proyecto:** Glea Nexo  
**Fecha:** 2026-06-03  
**Estado final:** IMPLEMENTADO y validado en replay exitoso con evidencia real; pendiente prueba controlada de fallo prolongado  
**Rama sugerida:** `exercise/24-nodered-real-backoff`

---

## 1. Qué resuelve

Este ejercicio implementa un replay real desde SQLite/outbox hacia el backend con control explícito de estado y política de reintentos.

Resuelve el gap dejado por el ejercicio 23:

- el outbox deja de ser solo un registro pasivo;
- el envío al backend pasa a depender de un worker de replay;
- el edge puede drenar backlog histórico sin enviar directamente desde MQTT al backend;
- el resultado del envío queda persistido localmente como transición de estado.

---

## 2. Estado anterior

Antes de este cambio, el flow tenía este comportamiento:

- MQTT insertaba en `outbox`;
- el tab `Bridge MQTT -> Backend` hacía `POST` directo al backend;
- no existía worker de replay desde SQLite;
- `tries`, `last_error`, `next_attempt_at` y `state` no gobernaban un flujo real de reintento;
- un mensaje podía llegar a PostgreSQL sin que SQLite reflejara ninguna transición operativa.

Conclusión: existía persistencia local, pero no un mecanismo real de replay controlado.

---

## 3. Decisión técnica aplicada

Se adopta una estrategia **outbox-first** en Node-RED.

### Cambios principales

- se desactiva el `MQTT -> HTTP` directo del bridge;
- MQTT sigue insertando telemetría en SQLite/outbox;
- un worker periódico selecciona mensajes elegibles por `state`, `tries` y `next_attempt_at`;
- el worker marca `SENDING` antes del `POST`;
- si el `POST` tiene éxito, actualiza a `SENT`;
- si falla, actualiza a `FAILED` con backoff;
- si se supera el máximo de intentos o el payload local está corrupto, pasa a `DEAD_LETTER`.

---

## 4. Política de retry y backoff

### Estados usados

```text
PENDING
SENDING
SENT
FAILED
DEAD_LETTER
```

### Selección de mensajes elegibles

```sql
SELECT id, message_id, topic, payload, tries
FROM outbox
WHERE state IN ('PENDING', 'FAILED')
  AND tries < 5
  AND datetime(next_attempt_at) <= datetime('now')
ORDER BY next_attempt_at ASC, id ASC
LIMIT 20;
```

### Backoff adoptado

```text
intento 1 -> 30s
intento 2 -> 60s
intento 3 -> 120s
intento 4+ -> 300s
máximo    -> 5 intentos
```

### Transiciones implementadas

```text
PENDING/FAILED -> SENDING -> SENT
PENDING/FAILED -> SENDING -> FAILED
PENDING/FAILED -> SENDING -> DEAD_LETTER
```

---

## 5. Archivos modificados

- `edge/nodered/flows/flows.json`
- `edge/nodered/data/flows.json`

Elementos relevantes del flow:

- `MQTT IN disabled (outbox-first)`
- `Replay tick 15s`
- `Select due outbox rows`
- `Prepare SENDING claim`
- `Mark SENDING`
- `Replay HTTP status`
- `Prepare SENT update`
- `Prepare FAILED or DLQ`
- `Catch replay HTTP errors`

---

## 6. Evidencia real en Raspberry Pi

### 6.1 Verificación de despliegue del flow

Comando:

```bash
grep -n 'MQTT IN disabled (outbox-first)\|Replay tick 15s\|Prepare FAILED or DLQ' ~/glea-nexo/edge/nodered/data/flows.json
```

Resultado observado:

```text
615:        "name": "MQTT IN disabled (outbox-first)",
741:        "name": "Replay tick 15s",
952:        "name": "Prepare FAILED or DLQ",
```

Conclusión: el runtime de Node-RED quedó desplegado con el worker de replay nuevo.

### 6.2 Drenado real del backlog histórico

Comando:

```bash
sqlite3 ~/glea-nexo/edge/sqlite/edge.db "
select state, count(*)
from outbox
group by state
order by state;
"
```

Estado observado tras arrancar el worker:

```text
PENDING|7339
SENT|60
```

Estado observado más tarde:

```text
PENDING|7040
SENT|360
```

Conclusión: el worker no solo existe, sino que está drenando backlog histórico desde SQLite hacia el backend.

### 6.3 Validación dirigida de transición a `SENT`

Mensaje de prueba publicado:

```text
messageId = ex24-20260603180606
```

Para no esperar a que el backlog antiguo dejara paso al mensaje nuevo, se forzó prioridad local:

```bash
sqlite3 ~/glea-nexo/edge/sqlite/edge.db "
update outbox
set next_attempt_at = '1970-01-01T00:00:00Z'
where message_id = '$MSG_ID';
"
```

Resultado observado tras el replay:

```text
7401|ex24-20260603180606|SENT|0|1970-01-01T00:00:00Z|2026-06-03T17:09:02.496Z|
```

Interpretación:

- el mensaje fue seleccionado y enviado;
- la transición a `SENT` funcionó;
- `sent_at` se persistió correctamente.

Este caso aún no validaba la limpieza de `next_attempt_at`, porque esa mejora se desplegó después.

### 6.4 Validación final de limpieza en éxito

Mensaje de prueba publicado tras el ajuste de limpieza:

```text
messageId = ex24-clean-2-20260603182155
```

Primera observación operativa:

```text
Error: stepping, database is locked (5)
```

Para evitar contención puntual con SQLite se usó:

```bash
sqlite3 ~/glea-nexo/edge/sqlite/edge.db "
PRAGMA busy_timeout = 5000;
update outbox
set next_attempt_at = '1970-01-01T00:00:00Z'
where message_id = '$MSG_ID';
"
```

Resultado final observado:

```text
7404|ex24-clean-2-20260603182155|SENT|0||2026-06-03T17:22:54.768Z|
```

Interpretación:

- `state = SENT`;
- `tries = 0`;
- `sent_at` queda informado;
- `next_attempt_at` queda `NULL`;
- `last_error` queda vacío.

Conclusión: el cierre exitoso del replay deja el registro local limpio y consistente.

---

## 7. Resultado técnico del ejercicio

El ejercicio se considera cerrado como:

```text
IMPLEMENTADO y validado en replay exitoso real
```

Justificación:

```text
La Raspberry Pi ejecuta un worker de replay real desde SQLite/outbox. Se ha demostrado con evidencia que el backlog histórico se drena, que mensajes concretos transicionan a SENT y que sent_at queda persistido. Además, tras el ajuste final, next_attempt_at queda limpio en éxito.
```

Matiz técnico:

```text
Aunque el mecanismo de replay y backoff está implementado, todavía no se ha documentado en este ejercicio una prueba controlada completa de FAILED -> retry -> SENT con backend caído y recuperación posterior.
```

---

## 8. Riesgos y limitaciones conocidas

- el backlog existente puede tardar bastante en vaciarse con `LIMIT 20` y tick de `15s`;
- SQLite puede devolver `database is locked` en operaciones manuales concurrentes con Node-RED;
- no se ha adjuntado todavía evidencia operativa de `FAILED` y reintento posterior exitoso;
- no se ha validado en esta iteración un caso real de `DEAD_LETTER`.

---

## 9. Decisiones técnicas tomadas

### Decisión 1 — Cambiar a outbox-first

**Problema:** el envío directo desde MQTT al backend dejaba al outbox como persistencia pasiva.  
**Decisión:** desactivar el `MQTT -> HTTP` directo del bridge y hacer que el backend reciba datos solo desde el worker de replay.  
**Tradeoff:** se gana consistencia operativa, pero el flujo depende del worker incluso para mensajes nuevos.  
**Riesgo:** si el worker se rompe, se acumula backlog.  
**Mitigación:** estado explícito en SQLite y queries operativas del ejercicio 23.

### Decisión 2 — Lotes pequeños y tick fijo

**Problema:** un replay agresivo puede saturar el backend.  
**Decisión:** procesar `20` filas por tick cada `15s`.  
**Tradeoff:** el drenado del backlog es más lento, pero el riesgo de ráfaga se reduce.  
**Riesgo:** tiempos largos de vaciado en backlog grande.  
**Mitigación:** ajustar `LIMIT` y frecuencia cuando existan métricas más estables.

### Decisión 3 — Limpiar `next_attempt_at` al cerrar en éxito

**Problema:** dejar `next_attempt_at` con un valor antiguo en un registro `SENT` ensucia la semántica del estado final.  
**Decisión:** poner `next_attempt_at = NULL` cuando se actualiza a `SENT`.  
**Tradeoff:** requiere una iteración adicional pequeña, pero deja el registro final más claro.  
**Riesgo:** ninguno funcional relevante.  
**Mitigación:** validación real con `messageId` nuevo en la Raspberry.

---

## 10. Done

- replay real implementado desde SQLite/outbox;
- transición `PENDING/FAILED -> SENDING -> SENT` validada con evidencia;
- `sent_at` persistido correctamente;
- `next_attempt_at` limpiado a `NULL` en éxito;
- backlog histórico drenándose en producción de prueba sobre Raspberry Pi;
- política de retry y backoff definida en el flow versionado.

---

## 11. Siguiente prueba recomendada

Para cerrar la evidencia operativa restante conviene ejecutar una prueba controlada con backend caído:

```text
backend down
-> mensaje queda FAILED
-> tries incrementa
-> next_attempt_at avanza con backoff
-> backend up
-> mismo mensaje termina en SENT
```

Eso cerraría la validación empírica completa de la rama de fallo del ejercicio 24.
