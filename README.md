# English Nova

English Nova is a full-stack English vocabulary learning site with a calm nature-inspired UI, multi-platform wordbook import, search, review, and progress tracking.

## Workspace

- `FrontEnd-EnglishNova`
  React + TypeScript + Vite frontend workspace.
- `BackEnd-EnglishNova`
  Legacy monolith backend kept as reference.
- `BackEnd-EnglishNova/distributed`
  Active Nacos-based distributed backend workspace with multiple Spring Boot services.

## Project Timeline

### Initial State

The repository started as two separate scaffolds:

- Frontend:
  default Vite React TypeScript template
- Backend:
  default Spring Boot Maven project with only basic web and Redis dependencies

At that stage:

- there was no product UI
- there were no business modules
- there was no import adapter layer
- there was no RabbitMQ or Elasticsearch integration
- backend config used a minimal single-line properties file

### Current State

The project is now organized as an MVP skeleton for the vocabulary product.

#### Frontend

Key files:

- `src/App.tsx`
- `src/App.css`
- `src/index.css`
- `src/data/appData.ts`
- `src/types.ts`

Current frontend capabilities:

- nature-themed landing and workspace shell
- dashboard view
- import center view
- review flow view
- search view
- progress view
- responsive layout for desktop and mobile
- soft motion with restrained transitions

#### Backend

Key files:

- `distributed/pom.xml`
- `distributed/english-nova-common`
- `distributed/gateway-service`
- `distributed/system-service`
- `distributed/study-service`
- `distributed/search-service`
- `distributed/import-service`

Current backend capabilities:

- service-per-domain split:
  - `gateway-service`
  - `system-service`
  - `study-service`
  - `search-service`
  - `import-service`
- shared DTO/common module for consistent API contracts
- Nacos service registration and config center integration
- MySQL bootstrap schema and seed data mounted through Docker
- starter APIs:
  - `/api/system/overview`
  - `/api/study/agenda`
  - `/api/search/words`
  - `/api/imports/presets`
  - `/api/imports/tasks`

## Current Architecture

### Frontend Responsibility

- display study workspace
- render import flow and progress surfaces
- support search interaction
- present calm and readable learning rhythm

### Backend Responsibility

- split concerns by service boundary instead of one all-in-one Spring Boot app
- keep DTOs and error handling in a shared module
- use Nacos for discovery and externalized config lookup
- route frontend traffic through a single gateway service
- persist study/search/import demo data in MySQL
- expose service-specific APIs behind the frontend reverse proxy

## Infrastructure Notes

The backend is already configured to read infrastructure connection info from environment variables:

- `MYSQL_HOST`
- `MYSQL_PORT`
- `MYSQL_DATABASE`
- `MYSQL_USERNAME`
- `MYSQL_PASSWORD`
- `REDIS_HOST`
- `REDIS_PORT`
- `REDIS_PASSWORD`
- `RABBITMQ_HOST`
- `RABBITMQ_PORT`
- `RABBITMQ_USERNAME`
- `RABBITMQ_PASSWORD`
- `RABBITMQ_VHOST`
- `ELASTICSEARCH_URIS`

Default values currently point to `localhost`-based ports.

## Docker Status

The repository now includes a full local Docker stack:

- `docker-compose.yml`
- `./.env.docker`
- `FrontEnd-EnglishNova/Dockerfile`
- `BackEnd-EnglishNova/distributed/Dockerfile`
- `docker/mysql/init`

Services in the stack:

- frontend: nginx static site with `/api` reverse proxy to gateway-service
- nacos: service registry and config center
- gateway-service: single ingress for frontend-to-backend traffic
- system-service: product overview service
- study-service: study agenda service
- search-service: vocabulary search service
- import-service: import preset and task service
- mysql: MySQL 8.4
- redis: Redis 7.4
- rabbitmq: RabbitMQ 4.1 with management UI
- elasticsearch: Elasticsearch 8.19 single-node

Start everything with:

```bash
docker compose --env-file .env.docker up --build -d
```

Default exposed ports:

- frontend: `3000`
- gateway-service: `8080`
- system-service: `8081`
- study-service: `8082`
- search-service: `8083`
- import-service: `8084`
- nacos: `8848`
- mysql: `3307`
- redis: `6379`
- rabbitmq: `5672`
- rabbitmq management: `15672`
- elasticsearch: `9200`

Stop everything with:

```bash
docker compose --env-file .env.docker down
```

## Suggested Next Steps

1. Adjust `./.env.docker` if you want non-default ports or credentials.
2. Run `docker compose --env-file .env.docker up --build -d`.
3. Confirm service health at:
   - `http://localhost:8080/actuator/health`
   - `http://localhost:8081/actuator/health`
   - `http://localhost:8082/actuator/health`
   - `http://localhost:8083/actuator/health`
   - `http://localhost:8084/actuator/health`
4. Confirm Nacos console at `http://localhost:8848/nacos`.
5. Confirm frontend at `http://localhost:3000`.
6. Extend the seed schema into business-grade tables.
7. Finish replacing remaining frontend mock content with live API data.

## Validation

The current scaffold has been verified with:

- frontend: `npm run build`
- distributed backend: `mvn -q -DskipTests package`
- Nacos registration verification: gateway-service, system-service, study-service, search-service, import-service all register successfully
