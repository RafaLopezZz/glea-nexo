# Ejercicio 7 — OpenAPI como contrato vivo

## Miniejercicio 2 — Documentar parámetros críticos

## Objetivo

Identificar los parámetros cuyo significado no puede quedar implícito y que deben quedar explicados en Swagger/OpenAPI para que la API pueda usarse correctamente sin necesidad de abrir el código.

## Qué pide realmente este miniejercicio

No basta con que Swagger liste parámetros. Este miniejercicio exige que los parámetros importantes queden documentados con su:

- semántica funcional,
- obligatoriedad u opcionalidad,
- formato,
- valor por defecto si existe,
- y efecto esperado sobre la respuesta o sobre posibles errores.

En otras palabras, OpenAPI debe explicar no solo **qué parámetro existe**, sino también **cómo se usa y qué significa**.

## Parámetros críticos identificados

### 1. `X-Org-Code`

#### Dónde aplica

Es un parámetro contractual de alto nivel para endpoints de inventory, ingest y observabilidad.

#### Qué debe documentarse

- que es un header HTTP;
- que es opcional;
- que si no se envía, el backend usa la organización `default`;
- que si la organización resuelta no existe, el backend puede responder `404`.

#### Por qué es crítico

Si no se documenta, el consumidor de la API no entiende por qué el mismo request puede funcionar en una organización y fallar en otra.

#### Riesgo si falta en Swagger

- dependencia de memoria tácita;
- errores `404` difíciles de interpretar;
- falsa impresión de que la API no es multi-organización.

## 2. `from` y `to`

#### Dónde aparecen

- `GET /api/telemetry/readings`
- `GET /api/telemetry/latest`
- `GET /api/alerts`

#### Qué debe documentarse

- que ambos son opcionales;
- que usan formato ISO-8601 UTC;
- que representan filtros por tiempo de evento (`event time`);
- que si ambos están presentes debe cumplirse `from <= to`;
- que el rango máximo permitido es de 2 años;
- que violaciones del contrato responden con `400 Bad Request`.

#### Por qué es crítico

Después del Bloque 2, estos parámetros forman parte del contrato endurecido del sistema. No pueden quedar descritos solo por nombre.

#### Riesgo si falta en Swagger

- uso ambiguo del tiempo;
- queries inválidas sin explicación clara;
- consumidores que filtran sobre un tiempo distinto al esperado.

## 3. `zoneId` y `deviceId` en observabilidad

#### Dónde aparecen

- `GET /api/telemetry/readings`
- `GET /api/telemetry/latest`
- `GET /api/alerts`

#### Qué debe documentarse

- que son UUID;
- que son opcionales;
- qué recurso filtran;
- si se pueden combinar;
- qué efecto tiene omitir ambos.

#### Por qué es crítico

Estos filtros cambian el alcance funcional de la consulta. Si no se explica su semántica, Swagger solo expone nombres técnicos.

## 4. Paginación en inventory

#### Dónde aparece

- `GET /api/farms`
- `GET /api/farms/{farmId}/zones`
- `GET /api/devices`

#### Parámetros

- `page`
- `size`
- `sort`

#### Qué debe documentarse

- valores por defecto (`page=0`, `size=20`, `sort=createdAt,desc`);
- que `page` es base 0;
- formato esperado de `sort`;
- si hay convenciones o límites prácticos de tamaño.

#### Por qué es crítico

La paginación afecta directamente a consumo, UX y reproducibilidad de consultas.

#### Riesgo si falta en Swagger

- clientes que interpretan mal la página inicial;
- ordenamientos erróneos;
- respuestas inesperadas por no conocer defaults.

## 5. Filtros de inventory

### En `GET /api/farms`

- `q`

Debe documentarse como filtro de búsqueda libre o textual sobre farms, si ese es su uso real.

### En `GET /api/farms/{farmId}/zones`

- `q`

Debe documentarse con la misma lógica, indicando que aplica dentro del scope de una finca concreta.

### En `GET /api/devices`

- `farmId`
- `zoneId`
- `state`
- `q`

#### Qué debe documentarse

- que `farmId` y `zoneId` son UUID opcionales;
- que `state` es un filtro por `OnlineState`;
- que `q` es un filtro textual;
- si los filtros pueden combinarse;
- si filtran sobre datos de negocio o sobre estado operativo.

#### Por qué es crítico

`GET /api/devices` es uno de los endpoints más expresivos y con más combinaciones posibles. Sin documentación clara, Swagger no basta como contrato de uso.

## 6. Path variables críticas

#### Casos relevantes

- `farmId`
- `zoneId`
- `deviceId`

#### Dónde aparecen

- CRUD de farms, zones y devices
- relaciones anidadas como `/farms/{farmId}/zones` y `/zones/{zoneId}/devices`

#### Qué debe documentarse

- que son UUID;
- qué recurso identifican;
- qué error cabe esperar si no existen (`404`);
- en qué casos representan contenedor padre y en qué casos recurso final.

## 7. Parámetros de ingest no obvios

### En `POST /api/ingest/readings/batch`

#### Campos importantes de request

- `source`
- `topic`
- `readings[]`
- `readings[].messageId`
- `readings[].deviceId`
- `readings[].ts`

#### Qué debe documentarse

- que `ts` es obligatorio y representa `event time`;
- que el `topic` debe ser compatible con el parser esperado por backend;
- que `messageId` participa en la deduplicación;
- que `deviceId` no es un simple texto irrelevante, sino parte del contexto de identidad del evento.

#### Por qué es crítico

Este endpoint es la entrada principal de telemetría. Si sus parámetros solo aparecen como estructura JSON sin explicación funcional, Swagger no actúa como contrato vivo.

## Resumen por endpoint

| Endpoint | Parámetros críticos a documentar |
|---|---|
| `GET /api/ping` | ninguno funcionalmente complejo |
| `POST /api/ingest/readings/batch` | `X-Org-Code`, `source`, `topic`, `readings[].messageId`, `readings[].deviceId`, `readings[].ts` |
| `GET /api/telemetry/readings` | `X-Org-Code`, `zoneId`, `deviceId`, `from`, `to` |
| `GET /api/telemetry/latest` | `X-Org-Code`, `zoneId`, `deviceId`, `from`, `to` |
| `GET /api/alerts` | `X-Org-Code`, `zoneId`, `deviceId`, `from`, `to` |
| `GET /api/farms` | `X-Org-Code`, `page`, `size`, `sort`, `q` |
| `GET /api/farms/{farmId}/zones` | `X-Org-Code`, `farmId`, `page`, `size`, `sort`, `q` |
| `GET /api/devices` | `X-Org-Code`, `page`, `size`, `sort`, `farmId`, `zoneId`, `state`, `q` |
| CRUD por id | `X-Org-Code` + path variable correspondiente |

## Conclusión del miniejercicio 2

Los parámetros críticos del sistema no se limitan a fechas. También incluyen multi-organización, paginación, filtros de inventory, identificadores de recursos y campos clave del contrato de ingest.

La mejora necesaria en Swagger no consiste solo en añadir annotations, sino en transformar nombres técnicos en un contrato entendible: qué parámetro existe, qué significa, cuándo es opcional, cómo afecta al resultado y qué errores puede provocar.

## Siguiente paso natural

Una vez identificados los parámetros críticos, el siguiente trabajo es revisar qué ejemplos reales de request/response y qué respuestas de error faltan para que Swagger sea usable sin abrir el código.
