# Runbook - Inventory CRUD

## Objetivo

Operar el CRUD mínimo de inventario (`Farm`, `Zone`, `Device`) del backend Glea Nexo con scoping por organización (`X-Org-Code`) y correlation id.

## Levantar plataforma (PowerShell)

```powershell
# Build plataforma
docker compose -f infra/compose/docker-compose.platform.yml build

# Levantar edge + platform
docker compose `
  -f infra/compose/docker-compose.edge.yml `
  -f infra/compose/docker-compose.platform.yml `
  up -d

# Estado
docker compose `
  -f infra/compose/docker-compose.edge.yml `
  -f infra/compose/docker-compose.platform.yml `
  ps
```

## Verificaciones rápidas

```powershell
Invoke-WebRequest -Uri http://localhost:8080/actuator/health -UseBasicParsing
Invoke-WebRequest -Uri http://localhost:8080/api/ping -UseBasicParsing
Invoke-WebRequest -Uri http://localhost:4200 -UseBasicParsing
```

## Contexto de organización (`X-Org-Code`)

- Si envías `X-Org-Code`, el backend opera en esa organización.
- Si no envías header, usa `default`.
- Si el código no existe, responde `404 NOT_FOUND`.

## Ejemplos PowerShell

### 1) Crear Farm

```powershell
$body = @{
  code = 'finca-demo'
  name = 'Finca Demo'
  location = @{ lat = 4.71; lng = -74.07 }
} | ConvertTo-Json -Depth 4

Invoke-WebRequest -Method POST `
  -Uri http://localhost:8080/api/farms `
  -Headers @{ 'Content-Type'='application/json'; 'X-Org-Code'='default'; 'X-Correlation-Id'='rbk-001' } `
  -Body $body
```

### 2) Listar Farms paginado

```powershell
Invoke-WebRequest -Method GET `
  -Uri "http://localhost:8080/api/farms?page=0&size=20&sort=code,asc&q=finca" `
  -Headers @{ 'X-Org-Code'='default' }
```

### 3) Crear Zone dentro de Farm

```powershell
$farmId = '<UUID_FARM>'
$body = @{
  code = 'zona-a'
  name = 'Zona A'
  geometry = @{ type='Polygon'; coordinates=@() }
} | ConvertTo-Json -Depth 8

Invoke-WebRequest -Method POST `
  -Uri "http://localhost:8080/api/farms/$farmId/zones" `
  -Headers @{ 'Content-Type'='application/json'; 'X-Org-Code'='default' } `
  -Body $body
```

### 4) Crear Device dentro de Zone

```powershell
$zoneId = '<UUID_ZONE>'
$body = @{
  deviceUid = 'gw-01'
  name = 'Gateway Zona A'
} | ConvertTo-Json

Invoke-WebRequest -Method POST `
  -Uri "http://localhost:8080/api/zones/$zoneId/devices" `
  -Headers @{ 'Content-Type'='application/json'; 'X-Org-Code'='default' } `
  -Body $body
```

### 5) Listar Devices con filtros

```powershell
Invoke-WebRequest -Method GET `
  -Uri "http://localhost:8080/api/devices?page=0&size=20&sort=createdAt,desc&farmId=<UUID_FARM>&state=UNKNOWN&q=gw" `
  -Headers @{ 'X-Org-Code'='default' }
```

### 6) Actualizar Device (sin cambiar deviceUid)

```powershell
$deviceId = '<UUID_DEVICE>'
$body = @{
  name = 'Gateway Zona A - Renombrado'
  state = 'ONLINE'
} | ConvertTo-Json

Invoke-WebRequest -Method PUT `
  -Uri "http://localhost:8080/api/devices/$deviceId" `
  -Headers @{ 'Content-Type'='application/json'; 'X-Org-Code'='default' } `
  -Body $body
```

`deviceUid` es inmutable en update. Solo se define en `POST /api/zones/{zoneId}/devices`.

### 7) Error esperado si se envía deviceUid en update

```powershell
$deviceId = '<UUID_DEVICE>'
$badBody = @{
  deviceUid = 'hack-uid'
  name = 'Intento inválido'
} | ConvertTo-Json

Invoke-WebRequest -Method PUT `
  -Uri "http://localhost:8080/api/devices/$deviceId" `
  -Headers @{ 'Content-Type'='application/json'; 'X-Org-Code'='default' } `
  -Body $badBody
```

Resultado esperado: `400 VALIDATION_ERROR` por propiedad no permitida en el DTO de update.

## Ejemplos curl

```bash
curl -X POST http://localhost:8080/api/farms \
  -H 'Content-Type: application/json' \
  -H 'X-Org-Code: default' \
  -d '{"code":"finca-curl","name":"Finca Curl"}'

curl 'http://localhost:8080/api/devices?page=0&size=10&sort=createdAt,desc' \
  -H 'X-Org-Code: default'
```

## Códigos esperados

- `201`: creación exitosa
- `200`: lectura/actualización exitosa
- `204`: borrado exitoso
- `400`: validación inválida
- `404`: recurso/organización no encontrado
- `409`: conflicto por constraint unique o FK
- `500`: error inesperado

## Troubleshooting rápido

```powershell
# Logs backend
docker compose -f infra/compose/docker-compose.platform.yml logs -f backend

# Ver flyway history
docker compose -f infra/compose/docker-compose.platform.yml exec -T postgres `
  psql -U glea -d glea_nexo -c "SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```
