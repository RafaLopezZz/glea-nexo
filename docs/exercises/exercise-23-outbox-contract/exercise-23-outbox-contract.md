# Ejercicio 23 — Contrato formal del outbox

**Proyecto:** Glea Nexo  
**Fecha:** 2026-06-03  
**Estado final:** CERRADO con contrato formalizado y gap de replay documentado  
**Rama sugerida:** `exercise/23-outbox-contract`

---

## 1. Qué resuelve

El ejercicio formaliza el outbox SQLite para que deje de ser una cola implícita basada solo en `sent_at is null`.

Se cierran tres piezas:

- estados explícitos de ciclo de vida;
- campos operativos mínimos para retries y auditoría local;
- consultas de inspección para operación y debugging.

---

## 2. Estado anterior

Antes de este cambio, el outbox tenía este contrato efectivo:

- inserción con `INSERT OR IGNORE`;
- deduplicación por `message_id`;
- `tries`, `last_error` y `sent_at` existían en schema, pero no se actualizaban;
- no existía `state`;
- no existía `next_attempt_at`;
- el bridge `MQTT -> Backend` seguía haciendo `POST` directo al backend y no consumía el outbox como fuente formal de replay.

Conclusión: el sistema tenía capacidad funcional parcial, pero no una semántica auditable del backlog local.

---

## 3. Contrato formal adoptado

### Estados

```text
PENDING     -> mensaje encolado y elegible para envio
SENDING     -> intento en curso
SENT        -> entrega confirmada
FAILED      -> intento fallido pero reintentable
DEAD_LETTER -> mensaje descartado de la ruta normal
```

### Diagrama de estados

```text
MQTT recibido
  -> PENDING

PENDING
  -> SENDING

SENDING
  -> SENT
  -> FAILED

FAILED
  -> PENDING
  -> DEAD_LETTER
```

### Campos mínimos

| Campo | Propósito |
|---|---|
| `message_id` | idempotencia local |
| `ts` | event time original del mensaje |
| `topic` | topic MQTT recibido |
| `payload` | payload íntegro para replay o análisis |
| `state` | estado explícito del mensaje |
| `tries` | número de intentos realizados |
| `last_error` | último error observado |
| `next_attempt_at` | instante mínimo para volver a intentar |
| `sent_at` | instante confirmado de envío |

---

## 4. Implementación aplicada

Se ha actualizado el contrato versionado en:

- `edge/sqlite/schema.sql`
- `edge/sqlite/migrations/2026-06-03-outbox-contract.sql`
- `edge/sqlite/outbox-inspection.sql`
- `edge/nodered/flows/flows.json`

Cambios concretos:

- `state` pasa a ser obligatorio con `CHECK` cerrado;
- `next_attempt_at` pasa a existir como campo obligatorio del contrato lógico;
- se añaden índices operativos por estado/próximo intento y por `sent_at`;
- las inserciones Node-RED nuevas ya nacen como `PENDING` con `next_attempt_at = now()`;
- el panel de estado del flow deja de inferir backlog solo por `sent_at` y resume por estados.

---

## 5. Queries operativas

Archivo: `edge/sqlite/outbox-inspection.sql`

Consultas incluidas:

- resumen por estado;
- backlog accionable (`PENDING`, `FAILED`, `DEAD_LETTER`);
- errores reintentables ordenados por `tries`.

Ejemplo rápido:

```sql
SELECT state, COUNT(*)
FROM outbox
GROUP BY state;
```

---

## 6. Migración para instalaciones existentes

Para una SQLite ya creada, aplicar:

```bash
sqlite3 edge/sqlite/edge.db < edge/sqlite/migrations/2026-06-03-outbox-contract.sql
```

La migración:

- añade `state` y `next_attempt_at`;
- clasifica filas históricas como `SENT`, `FAILED` o `PENDING`;
- rellena `next_attempt_at` con `ts` en datos antiguos;
- crea índices de inspección.

---

## 7. Gap que sigue abierto

Este ejercicio no implementa todavía el motor completo de retry/replay.

Sigue pendiente en el ejercicio 24:

- transición real `PENDING -> SENDING -> SENT/FAILED`;
- incremento de `tries`;
- persistencia real de `last_error`;
- cálculo de backoff y escritura de `next_attempt_at`;
- promoción opcional a `DEAD_LETTER`.

El contrato queda listo para ello, pero el bridge actual aún no lo ejecuta.

---

## 8. Done

- outbox con estados explícitos;
- campos mínimos formalizados;
- migración SQLite para entornos existentes;
- queries operativas versionadas;
- gap residual documentado sin ocultar la deuda técnica restante.
