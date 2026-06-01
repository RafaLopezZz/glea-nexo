# Ejercicio 1 — Preparar entorno edge real

**Proyecto:** Glea Nexo  
**Fecha:** 2026-06-01  
**Estado final:** REAL  
**Objetivo:** validar que el entorno edge real está operativo, conectado con la plataforma local y preparado para los ejercicios posteriores de ingest, offline/replay y observabilidad.

---

## 1. Resumen ejecutivo

El ejercicio se considera cerrado como **REAL** porque se ha validado que:

- La plataforma local está levantada con backend, frontend y PostgreSQL.
- La Raspberry Pi actúa como edge real con Mosquitto, Node-RED y `edge-python`.
- La Raspberry Pi puede alcanzar el backend local por red LAN.
- Node-RED puede alcanzar el backend desde dentro del contenedor.
- Mosquitto responde correctamente.
- Node-RED está levantado y healthy.
- `BACKEND_URL` está configurado en el contenedor Node-RED.
- `edge-python` ha dejado de ser un placeholder y ejecuta el simulador real.
- La dependencia `paho-mqtt` queda instalada correctamente.
- El simulador publica telemetría viva en MQTT bajo `agro/#`.

Este ejercicio no valida todavía la persistencia en PostgreSQL ni el replay offline. Eso corresponde a los ejercicios siguientes.

---

## 2. Arquitectura validada

La infraestructura está dividida en dos nodos físicos/lógicos.

| Nodo | IP | Servicios |
|---|---|---|
| Equipo local | `192.168.1.10` | Backend, Frontend, PostgreSQL |
| Raspberry Pi | `192.168.1.248` | Mosquitto, Node-RED, edge-python |

### Plataforma local

Contenedores observados en el equipo local:

```text
CONTAINER ID   IMAGE                    STATUS                    PORTS                              NAMES
2593c08ba649   compose-backend          Up / healthy              0.0.0.0:8080->8081/tcp             glea-backend
ef1f966b3e8b   compose-frontend         Up                         0.0.0.0:4200->4200/tcp             glea-frontend
ed46da924171   postgres:15              Up / healthy              0.0.0.0:3609->5432/tcp             glea-postgres
```

### Edge en Raspberry Pi

Contenedores observados en Raspberry Pi:

```text
NAME               IMAGE                            SERVICE       STATUS
gea-edge-python    python:3.11-slim                 edge-python   Up
glea-mosquitto     eclipse-mosquitto:2              mosquitto     Up
glea-nodered       nodered/node-red:4.1.10-debian   nodered       Up / healthy
```

---

## 3. Evidencias de conectividad

### 3.1 Backend local healthy

Desde el equipo local:

```powershell
Invoke-WebRequest -Uri http://localhost:8080/actuator/health -UseBasicParsing
```

Resultado observado:

```text
StatusCode: 200
StatusDescription: OK
X-Correlation-Id: c9ae68aa-a764-4a42-b3e7-4f877a5808e0
```

### 3.2 Raspberry Pi alcanza el backend local

Desde la Raspberry Pi:

```bash
curl -i http://192.168.1.10:8080/actuator/health
```

Resultado observado:

```http
HTTP/1.1 200
X-Correlation-Id: 3a01a5ab-bd04-483a-8e87-846e22adef8c
Content-Type: application/vnd.spring-boot.actuator.v3+json

{"status":"UP"}
```

### 3.3 Node-RED alcanza el backend desde dentro del contenedor

Desde el contenedor Node-RED:

```bash
docker compose -f infra/compose/docker-compose.edge.yml exec nodered sh
wget -S -O - http://192.168.1.10:8080/actuator/health
```

Resultado observado:

```http
HTTP/1.1 200
X-Correlation-Id: 78332dba-1505-4fd4-9100-8ee03c70a76a
Content-Type: application/vnd.spring-boot.actuator.v3+json
```

Conclusión: la ruta **Node-RED container → backend local** funciona correctamente.

---

## 4. Validación de Mosquitto

### Comando ejecutado

```bash
docker compose -f infra/compose/docker-compose.edge.yml exec mosquitto \
  mosquitto_sub -h localhost -t '$SYS/broker/uptime' -C 1 -v
```

### Resultado observado

```text
$SYS/broker/uptime 709258 seconds
```

Conclusión: Mosquitto está vivo y responde a tópicos internos `$SYS`.

---

## 5. Validación de Node-RED

### Comando ejecutado desde Raspberry Pi

```bash
curl -i http://localhost:1880
```

### Resultado observado

```http
HTTP/1.1 200 OK
Content-Type: text/html; charset=utf-8
<title>Node-RED</title>
```

Conclusión: Node-RED está levantado y accesible en `localhost:1880` desde la Raspberry Pi.

---

## 6. Configuración de `BACKEND_URL`

### Problema detectado inicialmente

Al principio, `BACKEND_URL` no estaba disponible en el contenedor Node-RED:

```bash
docker compose -f infra/compose/docker-compose.edge.yml config | grep -A5 -B5 BACKEND_URL
```

No devolvía resultado.

Y dentro del contenedor:

```bash
docker compose -f infra/compose/docker-compose.edge.yml exec nodered sh
env | grep BACKEND
```

Tampoco devolvía resultado.

### Solución aplicada

Se añadió la variable de entorno al servicio `nodered` en `infra/compose/docker-compose.edge.yml`:

```yaml
nodered:
  environment:
    BACKEND_URL: "http://192.168.1.10:8080"
```

### Validación final

```bash
docker compose -f infra/compose/docker-compose.edge.yml exec nodered sh -c 'env | grep BACKEND'
```

Resultado:

```text
BACKEND_URL=http://192.168.1.10:8080
```

Conclusión: Node-RED ya recibe por entorno la URL del backend local.

---

## 7. Activación del simulador Python

### Problema inicial

El servicio `edge-python` estaba configurado como placeholder:

```yaml
command: ["python", "-c", "import time; time.sleep(10**9)"]
```

Esto mantenía el contenedor vivo, pero no ejecutaba el simulador ni publicaba telemetría.

### Cambio inicial aplicado

Se cambió el comando para ejecutar el simulador:

```yaml
command: ["python", "/app/simulator/main.py"]
```

### Nuevo error detectado

Al arrancar el simulador apareció el siguiente error repetido en logs:

```text
Traceback (most recent call last):
  File "/app/simulator/main.py", line 8, in <module>
    import paho.mqtt.client as mqtt
ModuleNotFoundError: No module named 'paho'
```

Causa: la imagen `python:3.11-slim` no tenía instalada la dependencia `paho-mqtt` definida en `/app/requirements.txt`.

### Solución aplicada para desbloquear el ejercicio

Se modificó el comando de `edge-python` para instalar dependencias al arrancar y ejecutar después el simulador:

```yaml
edge-python:
  image: python:3.11-slim
  container_name: glea-edge-python
  working_dir: /app
  volumes:
    - ../../edge/python/services:/app
    - ../../edge/sqlite:/data/sqlite
  command: >
    sh -c "python -m pip install --no-cache-dir -r /app/requirements.txt && python /app/simulator/main.py"
  depends_on:
    - mosquitto
  restart: unless-stopped
  networks:
    - edge-net
```

### Recreación del servicio

```bash
docker compose -f infra/compose/docker-compose.edge.yml up -d --force-recreate edge-python
```

Resultado:

```text
[+] up 2/2
 ✔ Container glea-mosquitto    Running
 ✔ Container glea-edge-python  Recreated
```

### Validación del comando real

```bash
docker inspect glea-edge-python --format '{{json .Config.Cmd}}'
```

Resultado:

```json
["sh","-c","python -m pip install --no-cache-dir -r /app/requirements.txt && python /app/simulator/main.py"]
```

### Validación de dependencia `paho-mqtt`

```bash
docker compose -f infra/compose/docker-compose.edge.yml exec edge-python \
  python -c "import paho.mqtt.client as mqtt; print('paho ok')"
```

Resultado:

```text
paho ok
```

Conclusión: el simulador ya puede importar `paho-mqtt` correctamente.

---

## 8. Validación de telemetría viva en MQTT

### Comando ejecutado

```bash
timeout 90 docker compose -f infra/compose/docker-compose.edge.yml exec mosquitto \
  mosquitto_sub -h localhost -t 'agro/#' -v
```

### Resultado observado

Primero aparecen estados retenidos:

```text
agro/finca1/zona1/pi-gw-001/sensor/soil-01/status offline
agro/finca1/zona1/pi-gw-001/status online
```

Después aparecen lecturas vivas de telemetría:

```text
agro/finca1/zona1/pi-gw-001/sensor/soil_moisture-01/SOIL_MOISTURE/telemetry {"deviceId": "pi-gw-001", "sensorId": "soil_moisture-01", "type": "soil_moisture", "ts": "2026-06-01T16:12:20.372797+00:00", "value": 24.9, "unit": "%VWC", "battery": 4.03, "quality": "good", "messageId": "ad3d7ff0-5f08-470b-908d-ea4cfecf8e4d"}

agro/finca1/zona1/pi-gw-001/sensor/temperature-02/TEMPERATURE/telemetry {"deviceId": "pi-gw-001", "sensorId": "temperature-02", "type": "temperature", "ts": "2026-06-01T16:12:20.374228+00:00", "value": 25.5, "unit": "C", "battery": 3.81, "quality": "good", "messageId": "315440be-59f8-4286-9c93-407127885f59"}

agro/finca1/zona1/pi-gw-001/sensor/humidity-03/HUMIDITY/telemetry {"deviceId": "pi-gw-001", "sensorId": "humidity-03", "type": "humidity", "ts": "2026-06-01T16:12:20.375260+00:00", "value": 72.6, "unit": "%RH", "battery": 4.03, "quality": "good", "messageId": "acda11e9-f10a-40e1-b430-08e28fdf4b1d"}
```

También se observaron lecturas de:

- `SOIL_MOISTURE`
- `TEMPERATURE`
- `HUMIDITY`
- `EC`
- `PH`
- `LIGHT`
- `PRESSURE`
- `WIND`
- `RAIN`
- `BATTERY`

Conclusión: el simulador está publicando telemetría viva correctamente en Mosquitto.

---

## 9. Comandos finales de comprobación

### Estado de contenedores edge

```bash
docker compose -f infra/compose/docker-compose.edge.yml ps
```

Resultado esperado:

```text
NAME               IMAGE                            SERVICE       STATUS
glea-edge-python   python:3.11-slim                 edge-python   Up
glea-mosquitto     eclipse-mosquitto:2              mosquitto     Up
glea-nodered       nodered/node-red:4.1.10-debian   nodered       Up / healthy
```

### Variable de entorno Node-RED

```bash
docker compose -f infra/compose/docker-compose.edge.yml exec nodered sh -c 'env | grep BACKEND'
```

Resultado esperado:

```text
BACKEND_URL=http://192.168.1.10:8080
```

### Dependencia Python

```bash
docker compose -f infra/compose/docker-compose.edge.yml exec edge-python \
  python -c "import paho.mqtt.client as mqtt; print('paho ok')"
```

Resultado esperado:

```text
paho ok
```

### Telemetría MQTT

```bash
timeout 90 docker compose -f infra/compose/docker-compose.edge.yml exec mosquitto \
  mosquitto_sub -h localhost -t 'agro/#' -v
```

Resultado esperado:

```text
agro/finca1/zona1/pi-gw-001/sensor/.../.../telemetry {...}
```

---

## 10. Decisiones técnicas tomadas

### Decisión 1 — Mantener infraestructura dividida

**Problema:** la plataforma y el edge no corren en el mismo host.  
**Decisión:** mantener backend/frontend/PostgreSQL en el equipo local y Mosquitto/Node-RED/edge-python en Raspberry Pi.  
**Tradeoff:** es más realista para un escenario edge, pero introduce dependencia de red LAN.  
**Riesgo:** si cambia la IP del equipo local, Node-RED puede dejar de enviar datos al backend.  
**Mitigación:** reservar IP por DHCP o mover `BACKEND_URL` a `.env.edge` documentado.

### Decisión 2 — Parametrizar backend para Node-RED

**Problema:** Node-RED necesita saber a qué backend enviar los batches de ingest.  
**Decisión:** usar `BACKEND_URL=http://192.168.1.10:8080`.  
**Tradeoff:** fácil y explícito para desarrollo.  
**Riesgo:** la IP está acoplada al entorno local.  
**Mejora futura:** usar `.env.edge` o un perfil por entorno (`dev`, `rpi`, `pilot`).

### Decisión 3 — Instalar dependencias Python al arrancar

**Problema:** `edge-python` fallaba con `ModuleNotFoundError: No module named 'paho'`.  
**Decisión:** instalar `/app/requirements.txt` al arrancar antes de ejecutar el simulador.  
**Tradeoff:** desbloquea rápido el ejercicio.  
**Riesgo:** no es óptimo porque instala dependencias en cada arranque.  
**Mejora futura:** crear un Dockerfile propio para `edge-python`.

---

## 11. Limitaciones conocidas

- La instalación de dependencias en `edge-python` se hace en runtime; debe moverse a build mediante Dockerfile.
- La URL del backend está ligada a la IP LAN `192.168.1.10`; debería externalizarse en `.env.edge`.
- Este ejercicio no valida todavía que Node-RED transforme y envíe la telemetría al backend.
- Este ejercicio no valida persistencia en PostgreSQL.
- Este ejercicio no valida offline/replay.
- Mosquitto sigue abierto/anónimo si no se ha aplicado todavía hardening específico.

---

## 12. Criterio de cierre

| Criterio | Estado |
|---|---:|
| Platform local levantada | OK |
| Backend responde en local | OK |
| Raspberry Pi alcanza backend | OK |
| Node-RED alcanza backend desde contenedor | OK |
| Mosquitto responde | OK |
| Node-RED responde | OK |
| `BACKEND_URL` definido en Node-RED | OK |
| `edge-python` ejecuta simulador real | OK |
| `paho-mqtt` disponible | OK |
| Telemetría viva en `agro/#` | OK |

**Veredicto final:** REAL.

---

## 13. Próximo ejercicio recomendado

El siguiente paso lógico es el **Ejercicio 2 — Validar ingest normal end-to-end**.

Objetivo del siguiente ejercicio:

1. Tomar una lectura MQTT con `messageId` conocido.
2. Verla pasar por Node-RED.
3. Confirmar si entra en SQLite/outbox.
4. Confirmar si Node-RED hace `POST /api/ingest/readings/batch`.
5. Confirmar persistencia en PostgreSQL.
6. Consultar `/api/telemetry/latest` o `/api/telemetry/readings`.

Ese ejercicio ya no valida solo entorno: valida el flujo funcional completo MQTT → Node-RED → Backend → PostgreSQL → API.

---

## 14. Commit sugerido

Si los cambios quedaron en `infra/compose/docker-compose.edge.yml` y documentación:

```bash
git status
git add infra/compose/docker-compose.edge.yml
git add docs/exercises/exercise-01-prepare-real-edge/README.md
git commit -m "docs(edge): validate real edge environment setup"
```

Si decides crear también un Dockerfile para `edge-python`, hacerlo en una rama o commit separado:

```bash
git checkout -b chore/edge-python-dockerfile
git commit -m "chore(edge): build python simulator image with dependencies"
```
