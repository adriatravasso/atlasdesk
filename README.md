# AtlasDesk

AtlasDesk is an IT service management application foundation for managing support services and technology assets.

## Stack

- Backend: Java 21, Spring Boot 3.5, Spring MVC, Spring Data JPA, Hibernate, Jakarta Validation, PostgreSQL JDBC, Flyway, Maven Wrapper, JUnit 5.
- Frontend: React 18.3.1, React DOM 18.3.1, TypeScript strict mode, Vite, ESLint, Prettier, native CSS.
- Database: PostgreSQL 16 with Docker Compose.
- End-to-end: Cypress 15 in [`tests/e2e`](tests/e2e/README.md), run through the optional Docker Compose `e2e` profile.
- CI: Jenkins Declarative Pipeline in the root [`Jenkinsfile`](Jenkinsfile), using a Docker-enabled agent.

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

## Local demonstration

The separate `demo` profile seeds two fictional organizations, an ADMIN, AGENT, and REQUESTER in each organization, six assets per organization, and twelve tickets per organization. The seed uses stable organization names, account emails, asset numbers, and ticket titles so backend restarts do not duplicate records. Ticket states and history are created using the application's existing transition rules. All accounts use a password configured in the ignored `.env.demo` file.

Create the local settings file, set `DEMO_USER_PASSWORD` to a local-only password of 12–72 UTF-8 bytes, then start the isolated `atlasdesk-demo` Compose project:

```powershell
Copy-Item .env.demo.example .env.demo
# Edit .env.demo and set DEMO_USER_PASSWORD.
docker compose -p atlasdesk-demo --env-file .env.demo config --quiet
docker compose -p atlasdesk-demo --env-file .env.demo up -d --build --wait db backend frontend
docker compose -p atlasdesk-demo --env-file .env.demo ps
```

Open <http://localhost:5173>; the API health check is at <http://localhost:8080/api/health>. Demo login uses the generated organization ID (1 for Atlas Technology and 2 for Nexus Solutions on a new volume), the account email, and the password from `.env.demo`:

| Organization | Role | Email |
| --- | --- | --- |
| Atlas Technology | ADMIN | `admin@atlasdemo.local` |
| Atlas Technology | AGENT | `agent@atlasdemo.local` |
| Atlas Technology | REQUESTER | `requester@atlasdemo.local` |
| Nexus Solutions | ADMIN | `admin@nexusdemo.local` |
| Nexus Solutions | AGENT | `agent@nexusdemo.local` |
| Nexus Solutions | REQUESTER | `requester@nexusdemo.local` |

To confirm or inspect the synthetic records, check backend startup logs and query the database container:

```powershell
docker compose -p atlasdesk-demo --env-file .env.demo logs backend
docker compose -p atlasdesk-demo --env-file .env.demo exec db psql -U atlasdesk_demo -d atlasdesk_demo -c "select id, name, require_approval, sla_low_hours, sla_medium_hours, sla_high_hours from organizations order by id;"
docker compose -p atlasdesk-demo --env-file .env.demo exec db psql -U atlasdesk_demo -d atlasdesk_demo -c "select organization_id, status, count(*) from tickets group by organization_id, status order by organization_id, status;"
```

The demo profile is for local exploration only. The E2E project uses a separate PostgreSQL volume and its reset endpoint is available only to the E2E backend, so running Cypress does not reset or alter demonstration data. No demo password is committed. Stop the demo containers while retaining its named volumes with `docker compose -p atlasdesk-demo --env-file .env.demo stop`.

## Authentication

The React app provides a protected session shell, login, overview, ticket list/create/detail pages, asset directory, and ADMIN-only user and organization settings pages. Every role can open and follow tickets; the API restricts REQUESTER accounts to their own tickets. The asset directory is available to all roles; ADMIN and AGENT can create, edit, inactivate, and reactivate assets, while REQUESTER sees only active assets and the number, name, and type needed to choose one for a ticket. It uses the Fetch API with the HttpOnly session cookie; mutating requests first obtain CSRF and send its token in the named header. Login JSON contains `organizationId`, `email`, and `password`. `GET /api/auth/me` restores a session after reload. Do not store passwords or session identifiers in browser storage. Compose defaults `SESSION_COOKIE_SECURE` to `true`; the example `.env` explicitly sets it to `false` for local HTTP. Keep it `true` for HTTPS deployments.

The Cypress suite uses the isolated `atlasdesk-e2e` Compose project and its own persistent test-only PostgreSQL volume. It seeds fixed, non-production accounts and resets only that database before each test. It does not use `.env.demo`, the demonstration database, or the development admin. Headless runs write JUnit reports, videos, and failure screenshots under `tests/e2e/artifacts/`.

Run the complete suite headlessly with:

```powershell
docker compose -p atlasdesk-e2e --profile e2e run --rm e2e
```

The optional visual run starts a headed Chrome browser under Xvfb and exposes the VNC display only on the local computer:

```powershell
docker compose -p atlasdesk-e2e --profile e2e run --rm --service-ports e2e visual
```

While the suite runs, open <http://localhost:6080/vnc.html>. To run Cypress Open and choose a spec interactively, use `docker compose -p atlasdesk-e2e --profile e2e run --rm --service-ports e2e interactive`. The E2E frontend is also available at <http://localhost:5174> for manual inspection while its services are running. For one headed scenario, append `--spec cypress/e2e/authentication.cy.ts` after `visual`.

User administration is available to ADMIN sessions at `GET /api/users?page=0&size=20`, `POST /api/users`, and `PATCH /api/users/{id}`. Create requests require `name`, `email`, `role`, and `password`; `active` defaults to `true`. PATCH accepts only supplied fields. Send the CSRF cookie and token header for POST/PATCH. User queries and changes always use the authenticated session's organization; password hashes are never returned, and the last active ADMIN cannot be deactivated or demoted.

Assets are available at `GET /api/assets?page=0&size=20`, `POST /api/assets`, and `PATCH /api/assets/{id}`. Listing supports `search` (asset number or name), `type`, and `status` (`ACTIVE` or `INACTIVE`). ADMIN, AGENT, and REQUESTER sessions can list assets from their own organization; ADMIN and AGENT can create and update them. Create requests require `assetNumber`, `name`, and `type`; `description` is optional and `status` defaults to `ACTIVE`. PATCH accepts supplied fields, with an empty `description` clearing it. Asset numbers are unique without regard to case within each organization. The API derives organization scope from the session and does not accept organization IDs from clients.

`GET /api/dashboard` returns ticket counts, priority distribution, overdue tickets, and ACTIVE asset count for the authenticated organization. ADMIN and AGENT counts cover all organization tickets; REQUESTER counts cover only tickets they requested, while ACTIVE asset count is organization-wide. `totalTickets` includes every ticket status and equals the sum of OPEN, IN_PROGRESS, PENDING_APPROVAL, RESOLVED, and CLOSED. A ticket is overdue only when `dueAt` is earlier than current UTC time and status is OPEN, IN_PROGRESS, or PENDING_APPROVAL; RESOLVED and CLOSED are excluded. Counts are computed by database aggregate queries; the endpoint does not load ticket rows into the application. The organization/status and organization/requester ticket indexes remain available for these scoped queries, and ACTIVE asset count filters on organization/status using the matching asset index.

Tickets are available at `POST /api/tickets`, `GET /api/tickets?page=0&size=20`, `GET /api/tickets/{id}`, and `GET /api/tickets/{id}/history`. Create requests require a title, description, and `priority` (`LOW`, `MEDIUM`, or `HIGH`); `assetId` is optional. The requester is always the authenticated user, the initial status is `OPEN`, and organization scope comes from the session. REQUESTER sees only their own tickets; ADMIN and AGENT see tickets in their organization. Listing supports `search` by title, `status`, and `priority`, with page sizes from 1 to 100. Responses include requester, optional assignee and asset summaries, UTC timestamps, and a computed `overdue` flag. `dueAt` is a creation-time snapshot calculated from the organization's SLA in elapsed hours. New organizations start with LOW=72, MEDIUM=24, and HIGH=8; existing organizations keep their configured values through migration. `GET /api/settings` returns the active organization's SLA values to authenticated users. ADMIN can update all three values with `PATCH /api/settings` using `slaLowHours`, `slaMediumHours`, and `slaHighHours`, each an integer from 1 to 720. Updates apply only to newly created tickets: changing a ticket's priority later does not recalculate its deadline, and SLA changes never rewrite historical `dueAt` values. `overdue` is evaluated against current UTC time when a ticket is read, is false when there is no deadline or the ticket is RESOLVED/CLOSED, and remains applicable to OPEN, IN_PROGRESS, and PENDING_APPROVAL tickets. The UI formats the UTC deadline in the browser's local timezone and uses the API's `overdue` value without reimplementing the SLA rule; no background job is used. The history endpoint returns creation, assignment and workflow events in chronological order to users who can view that ticket. It uses UTC timestamps and the actor name captured when each event occurred. Existing tickets receive a creation event from their original requester and creation time; prior assignment and transition events are not reconstructed. ADMIN and AGENT assign active agents with `PATCH /api/tickets/{id}/assign` and an `assignedAgentId`. `POST /api/tickets/{id}/transition` accepts `START`, `RESOLVE`, `APPROVE`, `REJECT`, and `CLOSE`; invalid status transitions and concurrent updates return HTTP 409. The effective transition matrix is:

| Action | From | To | Allowed actor |
| --- | --- | --- | --- |
| `START` | OPEN | IN_PROGRESS | Assigned AGENT or ADMIN |
| `RESOLVE` | IN_PROGRESS | PENDING_APPROVAL when the organization requires approval; otherwise RESOLVED | Assigned AGENT; ADMIN may resolve directly |
| `APPROVE` | PENDING_APPROVAL | RESOLVED | ADMIN in the same organization |
| `REJECT` | PENDING_APPROVAL | IN_PROGRESS | ADMIN in the same organization |
| `CLOSE` | RESOLVED | CLOSED | Ticket requester or ADMIN |

An agent's resolution request does not set `resolvedAt` while pending. Approval records the resolution time; rejection leaves the ticket in progress so work can continue. Organization and requester visibility boundaries remain enforced.

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

Run the complete Cypress suite on demand. Compose starts its isolated test database, backend, and frontend; the default development services and data are not used:

```powershell
docker compose -p atlasdesk-e2e --profile e2e run --rm e2e
```

The isolated project name used above is important when the demo and tests run together. The E2E project also has a container-only TypeScript check; see [`tests/e2e/README.md`](tests/e2e/README.md) for the headed noVNC mode and evidence paths.

## Jenkins CI

Create a **Multibranch Pipeline** job in Jenkins with the GitHub Branch Source integration already available on that instance, connect it to `adriatravasso/atlasdesk` using the configured GitHub credentials, and keep the script path as `Jenkinsfile` at the repository root. Enable pull request discovery in the branch source so branches and PRs receive build status feedback. Assign the Docker-enabled Linux executor the label `docker-compose` used by the pipeline.

The selected Linux agent must provide Docker CLI, Docker Compose v2, and access to a Docker Engine. It does not need Java, Maven, Node.js, npm, PostgreSQL, or Cypress installed: each validation runs inside the existing Compose images. The Jenkins agent manages Docker externally; application containers do not mount the Docker socket.

Each build sets a unique `COMPOSE_PROJECT_NAME`, uses only the isolated `e2e` database and synthetic test accounts, and waits for the Compose health checks before Cypress runs. The pipeline archives Maven Surefire XML, Cypress JUnit reports, screenshots, and videos when present. Cleanup removes that build's containers and network without removing named volumes; build-specific test volumes are isolated from development and can be removed by the Jenkins infrastructure owner under that runner's retention policy.

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
- Organization-scoped SLA settings with ADMIN-only updates, stable ticket deadlines, and dynamically calculated overdue status.
- Role-checked ticket assignment, optional ADMIN approval/rejection of agent resolutions, and ticket transition timestamps with optimistic locking.
- React authenticated shell, session restoration, login/logout, role-protected user directory, role-aware asset inventory UI, ticket create/list/detail pages, and Vite API proxy.
- PostgreSQL 16, backend, and frontend Compose services with health checks, service dependencies, bind mounts, and persistent caches.
- Production image stages for the backend and frontend Dockerfiles.
- Cypress E2E smoke test and isolated dependency lockfile under `tests/e2e`, run in an opt-in Compose profile.

## Planned

Optional resolution approval and operational dashboards are available. Jenkins CI runs through the root declarative pipeline on a Docker-enabled agent.
