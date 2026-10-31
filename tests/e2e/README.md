# AtlasDesk Cypress E2E

Run the full headless suite from the repository root:

```powershell
docker compose --profile e2e run --rm e2e
```

Compose starts a dedicated PostgreSQL service, backend, and frontend for Cypress. These services use the internal Docker network and do not publish ports. They never connect to the development `db`, `backend`, or `frontend` services. The test database is stored in `e2e_postgres_data`; the E2E profile resets only this database before each test and seeds two organizations with fixed local test users. The test password and accounts in `cypress/fixtures/accounts.json` are synthetic and must never be used outside this isolated environment.

The suite covers invalid and valid login, session restore/logout, protected API access, user and asset administration, organization authorization boundaries, ticket creation, priority SLA snapshots, assignment, workflow, resolution approval, history, dashboard metrics, and organization separation. Browser flows exercise the real frontend and backend; a small set of UI-only error/loading cases use API interception. `portfolio-screenshots.cy.ts` is an end-to-end walkthrough against the same real services; it resets only the E2E database and captures real browser screenshots into the repository's `docs/screenshots/` directory.

Source, fixtures, configuration, and the npm lockfile live under `tests/e2e`. The E2E directory is bind-mounted. Cypress screenshots are written through a dedicated bind mount to `docs/screenshots/`; video recording is disabled. Node.js, npm, Chrome, and Cypress run inside the test container, with npm and Cypress caches in named volumes.

To regenerate only the portfolio images from the repository root:

```powershell
docker compose --profile e2e run --rm --entrypoint sh e2e -c "npm ci --prefer-offline && npx cypress run --browser chrome --spec cypress/e2e/portfolio-screenshots.cy.ts"
```

Type-check the Cypress project in its container:

```powershell
docker compose --profile e2e run --rm --entrypoint sh e2e -c "npm ci --prefer-offline && npm run typecheck"
```

Do not run `docker compose down -v`; the named development and E2E database volumes are persisted independently.
