# Development

AtlasDesk's development, build, and validation environment is Docker Compose. The host needs Git and Docker Compose v2; do not install Java, Maven, Node.js, npm, PostgreSQL, Chrome, or Cypress locally.

## Start the application

Run from the repository root:

```powershell
Copy-Item .env.example .env
docker compose --profile e2e config --quiet
docker compose up -d --build --wait db backend frontend
docker compose ps
```

Open <http://localhost:5173>. The backend is at <http://localhost:8080>, and its health endpoint is <http://localhost:8080/api/health>. Frontend and backend source are bind-mounted for live editing. PostgreSQL data and dependency caches use named volumes.

The checked-in `.env.example` contains synthetic local database settings and leaves `DEV_ADMIN_PASSWORD` empty. To sign in locally, set a private password in the ignored `.env` file before starting the backend. The development seeder creates `atlasdesk-admin@localhost.test` only with the `dev` profile. Use a local-only password and never reuse it in a hosted environment.

If a port is already occupied, change `POSTGRES_PORT`, `BACKEND_HOST_PORT`, or `FRONTEND_HOST_PORT` in `.env`. Internal service ports and Docker DNS names remain unchanged.

## Local demonstration data

The isolated `demo` profile seeds two fictional organizations, demo users for the existing roles, assets, and tickets. Set `DEMO_USER_PASSWORD` in the ignored `.env.demo` file; the password is not committed.

```powershell
Copy-Item .env.demo.example .env.demo
# Edit .env.demo and set DEMO_USER_PASSWORD.
docker compose -p atlasdesk-demo --env-file .env.demo config --quiet
docker compose -p atlasdesk-demo --env-file .env.demo up -d --build --wait db backend frontend
```

For a fresh demo volume, the organizations are Atlas Technology (`1`) and Nexus Solutions (`2`). Stop the demo with `docker compose -p atlasdesk-demo --env-file .env.demo stop` to preserve its data.

## Validation

Start the development services first, then run checks in the application containers:

```powershell
docker compose exec backend mvn -B -ntp test
docker compose exec backend mvn -B -ntp verify
docker compose exec frontend npm run lint
docker compose exec frontend npm run typecheck
docker compose exec frontend npm run format:check
docker compose exec frontend npm run build
```

The Cypress `e2e` profile uses its own PostgreSQL database, frontend, and backend. It seeds synthetic accounts from `tests/e2e/cypress/fixtures/accounts.json`, resets only its isolated database, and never connects to the development database.

```powershell
docker compose --profile e2e run --rm e2e
```

To regenerate only the six portfolio screenshots in `docs/screenshots/`:

```powershell
docker compose --profile e2e run --rm --entrypoint sh e2e -c "npm ci --prefer-offline && npx cypress run --browser chrome --spec cypress/e2e/portfolio-screenshots.cy.ts"
```

The screenshot workflow uses only synthetic data from the isolated E2E database. See the [E2E guide](../tests/e2e/README.md) for Cypress setup and reports.

## Continuous integration

The root `Jenkinsfile` is a Declarative Multibranch Pipeline. The agent needs Docker CLI, Compose v2, and a Docker Engine; application runtimes and browsers run in containers. Each build uses a unique Compose project, archives test reports and screenshots, and removes build containers and networks while preserving named volumes. Application containers do not mount the Docker socket.

## Stop services

Stop the local development services while preserving PostgreSQL data and dependency caches:

```powershell
docker compose down
```

Do not use `docker compose down -v` for routine development; it deletes named volumes.
