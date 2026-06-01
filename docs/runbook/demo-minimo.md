# Runbook mínimo — Glea Nexo

## 1. Prerrequisitos
- Docker y Docker Compose operativos
- Si vas a correr backend fuera de Docker: Java 21 + Maven 3.9+
- Si vas a correr frontend fuera de Docker: Node 20+

## 2. Validación mínima de Docker
```bash
docker version
docker run --rm hello-world
```

Si esto falla, no sigas con Testcontainers: el problema es de acceso a Docker.

## 3. Levantar demo completa con Docker
```bash
docker compose -f infra/compose/docker-compose.platform.yml up -d --build
```

Checks:
```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/ping
curl http://localhost:4200
```

## 4. Backend local sin Testcontainers (validación mínima reproducible)
Base de datos:
```bash
docker compose -f infra/compose/docker-compose.platform.yml up -d postgres
```

Backend:
```bash
cd backend
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:3609/glea_nexo
export SPRING_DATASOURCE_USERNAME=glea
export SPRING_DATASOURCE_PASSWORD=glea_123
mvn spring-boot:run
```

Checks:
```bash
curl http://localhost:8081/actuator/health
curl http://localhost:8081/api/ping
```

## 5. Frontend local
```bash
cd frontend
npm install
npm run build
npm run start -- --host 0.0.0.0 --port 4200
```

## 6. Validación funcional rápida
- Abrir `http://localhost:4200`
- Verificar que cargan filtros
- Verificar que no hay error CORS en consola
- Verificar llamadas a:
  - `GET /api/farms`
  - `GET /api/devices`
  - `GET /api/telemetry/latest?from=...&to=...`
  - `GET /api/telemetry/readings?from=...&to=...`
  - `GET /api/alerts?from=...&to=...`

## 7. Si quieres seguir con Testcontainers
Antes de `mvn test`, prueba:
```bash
docker version
docker info
docker run --rm postgres:16-alpine pg_isready
```

Y si tu cliente Docker nuevo da guerra con librerías viejas, fuerza compatibilidad temporal:
```bash
export DOCKER_API_VERSION=1.47
mvn test
```

Si así arranca, el problema no es de negocio sino de compatibilidad cliente/API Docker en el entorno de tests.
