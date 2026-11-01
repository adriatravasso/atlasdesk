# AtlasDesk Cypress E2E

The Cypress project uses the `e2e` Compose profile and a separate Compose project name. Its PostgreSQL named volume is independent of the manual demonstration project's `postgres_data` volume. The E2E seeder and `POST /api/e2e/reset` exist only with the backend's `e2e` Spring profile. Cypress resets only this disposable database before each spec/test through `cypress/support/e2e.ts`.

The fixed synthetic test identities in `cypress/fixtures/accounts.json` are for the E2E database only. The local demo uses separate accounts and a password in the ignored `.env.demo`; never reuse E2E credentials outside tests.

## Start E2E services

From the repository root, start the test-only database, API, and frontend. Their application traffic uses Compose service names; the frontend is optionally exposed at `localhost:5174` for manual inspection.

```powershell
docker compose -p atlasdesk-e2e --profile e2e config --quiet
docker compose -p atlasdesk-e2e --profile e2e up -d --build --wait e2e-db e2e-backend e2e-frontend
docker compose -p atlasdesk-e2e --profile e2e ps
```

Run the complete suite headlessly:

```powershell
docker compose -p atlasdesk-e2e --profile e2e run --rm e2e
```

Run one spec headlessly:

```powershell
docker compose -p atlasdesk-e2e --profile e2e run --rm e2e headless --spec cypress/e2e/authentication.cy.ts
```

## Watch Cypress in the browser

The test image extends the official Cypress browser image with Xvfb, x11vnc, and noVNC. The VNC protocol remains inside the test container; Compose publishes the noVNC web port on `127.0.0.1` only.

Run all specs sequentially in a visible Chrome browser:

```powershell
docker compose -p atlasdesk-e2e --profile e2e run --rm --service-ports e2e visual
```

Open <http://localhost:6080/vnc.html> while the command is running. A single headed spec can be watched with:

```powershell
docker compose -p atlasdesk-e2e --profile e2e run --rm --service-ports e2e visual --spec cypress/e2e/authentication.cy.ts
```

For interactive Cypress Open, where a spec can be selected and debugged:

```powershell
docker compose -p atlasdesk-e2e --profile e2e run --rm --service-ports e2e interactive
```

Open <http://localhost:6080/vnc.html> and use the Cypress desktop. This process remains attached until Cypress Open is closed or stopped with Ctrl+C.

## Evidence and validation

Headless and headed `cypress run` both emit JUnit XML reports and per-spec MP4 videos. Cypress stores screenshots when a test fails. All artifacts go into `tests/e2e/artifacts/`, which is ignored by Git and remains available on the host after the runner exits.

```text
tests/e2e/artifacts/
  reports/       results-<hash>.xml
  screenshots/   failure screenshots
  videos/        Cypress spec recordings
```

Type-check Cypress in its container:

```powershell
docker compose -p atlasdesk-e2e --profile e2e run --rm --entrypoint sh e2e -c "npm ci --prefer-offline && npm run typecheck"
```

The existing real browser suite covers login/logout/session authorization, ticket create/list/detail/filter and workflow transitions, conditional resolution approval, history, dashboard metrics, asset and user administration, organization settings, permission checks, and organization isolation. It uses the real Compose frontend, API, and PostgreSQL database. There are no external API dependencies.

Stop E2E containers while preserving its test data and dependency caches:

```powershell
docker compose -p atlasdesk-e2e --profile e2e stop
```

The stopped project is safe to start again. Do not run `docker compose down -v`; this task never needs volume deletion or a Docker-wide cleanup.
