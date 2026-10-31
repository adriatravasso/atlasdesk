# AtlasDesk

AtlasDesk is an IT service management application foundation for managing support services and technology assets.

## Stack

- Backend: Java 21, Spring Boot 3.5, Spring MVC, Spring Data JPA, Hibernate, Jakarta Validation, PostgreSQL JDBC, Flyway, Maven Wrapper, JUnit 5.
- Frontend: React 18.3.1, React DOM 18.3.1, TypeScript strict mode, Vite, ESLint, Prettier, native CSS.
- Database: PostgreSQL 16 with Docker Compose.
- End-to-end: Cypress 15 in [`tests/e2e`](tests/e2e/README.md), run through the optional Docker Compose `e2e` profile.

## Architecture

The application is a modular monolith. The API uses the base package `com.adria.atlasdesk`; future packages will be introduced only when their features are implemented. The frontend calls the API through Vite's `/api` proxy, which targets the `backend` Compose service.

## Prerequisites

- Docker Desktop or Docker Engine with Docker Compose v2

Java, Maven, Node.js, npm, and PostgreSQL are provided or run inside containers; they do not need to be installed on the host.

## Start development

Copy the illustrative development settings, validate Compose, and start the application services from the project root:

```powershell
Copy-Item .env.example .env
docker compose config
docker compose up -d --build db backend frontend
docker compose ps
```

Compose waits for PostgreSQL to become healthy before starting the backend, then waits for the API before starting the frontend. Open the frontend at <http://localhost:5173>; the API is available at <http://localhost:8080/api/health>. PostgreSQL is published on port `5432` by default.

The backend and frontend source directories are bind-mounted. Vite refreshes frontend changes automatically. Restart the backend after Java source changes so Maven recompiles them:

```powershell
docker compose restart backend
```

Named volumes preserve PostgreSQL data and Maven, npm, Cypress, frontend dependency, E2E dependency, and backend build caches across container restarts. The example database credentials are for local development only. Set matching `POSTGRES_*` and `DB_*` values in `.env` when changing them.

Build the application images or follow service logs when needed:

```powershell
docker compose build backend frontend
docker compose logs -f db backend frontend
```

## Validate inside containers

Start the stack first, then run the checks in the relevant service containers:

```powershell
docker compose exec backend mvn test
docker compose exec backend mvn verify
docker compose exec frontend npm run lint
docker compose exec frontend npm run typecheck
docker compose exec frontend npm run format:check
docker compose exec frontend npm run build
```

Run the Cypress browser smoke test on demand. Compose starts its dependent services and removes the one-off test container when it exits. The config, spec, package manifest, and lockfile live in `tests/e2e`:

```powershell
docker compose --profile e2e run --rm e2e
```

The E2E project also has a container-only TypeScript check; see [`tests/e2e/README.md`](tests/e2e/README.md).

Flyway runs during backend startup. No business tables or placeholder migrations are created.

## Stop development

```powershell
docker compose down
```

This stops and removes the containers while preserving named volumes and local database data. Do not use volume-removal options for routine shutdown.

## Implemented

- Spring Boot API with `GET /api/health`, returning `{"status":"UP","service":"atlasdesk-api"}`.
- PostgreSQL datasource configuration and Flyway integration.
- A web MVC test for the health endpoint.
- React welcome page with live API connection status and Vite API proxy.
- PostgreSQL 16, backend, and frontend Compose services with health checks, service dependencies, bind mounts, and persistent caches.
- Production image stages for the backend and frontend Dockerfiles.
- Cypress E2E smoke test and isolated dependency lockfile under `tests/e2e`, run in an opt-in Compose profile.

## Planned

Authentication and authorization, organizations and users, support tickets, IT assets, configurable SLAs, optional resolution approval, operational dashboards, broader end-to-end coverage, and Jenkins CI are planned for later tasks.
