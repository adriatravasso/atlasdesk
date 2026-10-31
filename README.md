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

The Compose backend explicitly activates the `dev` Spring profile by default, which idempotently seeds the `AtlasDesk Local` organization. It creates no user unless `DEV_ADMIN_PASSWORD` is supplied in the ignored `.env` file. When supplied, it creates the local ADMIN account `atlasdesk-admin@localhost.test` with that password. This account is dev-profile-only. Set `SPRING_PROFILES_ACTIVE` in `.env` to change the active profile; production runs should not activate `dev`.

## Authentication

The React app provides a protected session shell, login, overview, ticket list/create/detail pages, asset directory, and ADMIN-only user directory. Every role can open and follow tickets; the API restricts REQUESTER accounts to their own tickets. The asset directory is available to all roles; ADMIN and AGENT can create, edit, inactivate, and reactivate assets, while REQUESTER sees only active assets and the number, name, and type needed to choose one for a ticket. It uses the Fetch API with the HttpOnly session cookie; mutating requests first obtain CSRF and send its token in the named header. Login JSON contains `organizationId`, `email`, and `password`. `GET /api/auth/me` restores a session after reload. Do not store passwords or session identifiers in browser storage. `SESSION_COOKIE_SECURE` defaults to `false` for local HTTP; set it to `true` when serving the application over HTTPS.

For a local authenticated smoke test, set a development password in `.env` (use at least 12 characters) and restart the backend. The seeded `AtlasDesk Local` organization is normally ID `1`; confirm it with `docker compose exec db psql -U atlasdesk -d atlasdesk -c "select id, name from organizations;"`. Set `DEV_ADMIN_ORGANIZATION_ID` to that ID in `.env`. The Cypress profile reads the same email, organization ID, and password. Optional user and asset flows require `ATL0008_E2E_USER_EMAIL` and `ATL0010_E2E_ASSET_NUMBER` to contain new, unique test values. Set `ATL0012_E2E_TICKET_TITLE` to a unique title to create a ticket and verify its list entry and detail. The user flow leaves its account inactive and the asset flow leaves its asset inactive in the local database. Then run:

```powershell
docker compose up -d --build db backend frontend
docker compose --profile e2e run --rm e2e
```

Without `DEV_ADMIN_PASSWORD`, the invalid-credentials smoke check still runs and the real-session Cypress case is skipped.

User administration is available to ADMIN sessions at `GET /api/users?page=0&size=20`, `POST /api/users`, and `PATCH /api/users/{id}`. Create requests require `name`, `email`, `role`, and `password`; `active` defaults to `true`. PATCH accepts only supplied fields. Send the CSRF cookie and token header for POST/PATCH. User queries and changes always use the authenticated session's organization; password hashes are never returned, and the last active ADMIN cannot be deactivated or demoted.

Assets are available at `GET /api/assets?page=0&size=20`, `POST /api/assets`, and `PATCH /api/assets/{id}`. Listing supports `search` (asset number or name), `type`, and `status` (`ACTIVE` or `INACTIVE`). ADMIN, AGENT, and REQUESTER sessions can list assets from their own organization; ADMIN and AGENT can create and update them. Create requests require `assetNumber`, `name`, and `type`; `description` is optional and `status` defaults to `ACTIVE`. PATCH accepts supplied fields, with an empty `description` clearing it. Asset numbers are unique without regard to case within each organization. The API derives organization scope from the session and does not accept organization IDs from clients.

Tickets are available at `POST /api/tickets`, `GET /api/tickets?page=0&size=20`, and `GET /api/tickets/{id}`. Create requests require a title, description, and `priority` (`LOW`, `MEDIUM`, or `HIGH`); `assetId` is optional. The requester is always the authenticated user, the initial status is `OPEN`, and organization scope comes from the session. REQUESTER sees only their own tickets; ADMIN and AGENT see tickets in their organization. Listing supports `search` by title, `status`, and `priority`, with page sizes from 1 to 100. Responses include requester, optional assignee and asset summaries, and UTC timestamps. `dueAt` is a creation-time snapshot calculated from the organization SLA in elapsed hours; the initial migration defaults are LOW=72, MEDIUM=24, and HIGH=4. Later SLA changes do not alter existing tickets.

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

Flyway applies versioned schema migrations during backend startup. The initial migration creates organization and user tables; JPA validates the schema rather than updating it.

## Stop development

```powershell
docker compose down
```

This stops and removes the containers while preserving named volumes and local database data. Do not use volume-removal options for routine shutdown.

## Implemented

- Spring Boot API with `GET /api/health`, returning `{"status":"UP","service":"atlasdesk-api"}`.
- PostgreSQL datasource configuration and Flyway integration.
- A web MVC test for the health endpoint.
- Organization-scoped asset API with paginated filters, ADMIN/AGENT writes, and PostgreSQL uniqueness for asset numbers.
- Organization-scoped ticket creation, paginated/filterable listing, ticket details, requester ownership, optional same-organization asset association, and SLA deadline snapshots.
- React authenticated shell, session restoration, login/logout, role-protected user directory, role-aware asset inventory UI, ticket create/list/detail pages, and Vite API proxy.
- PostgreSQL 16, backend, and frontend Compose services with health checks, service dependencies, bind mounts, and persistent caches.
- Production image stages for the backend and frontend Dockerfiles.
- Cypress E2E smoke test and isolated dependency lockfile under `tests/e2e`, run in an opt-in Compose profile.

## Planned

Ticket assignment and transitions, configurable SLAs, optional resolution approval, operational dashboards, broader end-to-end coverage, and Jenkins CI are planned for later tasks.
