# AtlasDesk E2E tests

The Cypress project is run in the `e2e` Docker Compose profile. It verifies invalid credentials and, when configured, real login, session reload, protected user access, and logout. No local Node.js, npm, or Cypress installation is needed.

From the repository root, start the application and run the smoke test:

```powershell
docker compose up -d --build db backend frontend
docker compose --profile e2e run --rm e2e
```

For the real-session case, set `DEV_ADMIN_PASSWORD` and `DEV_ADMIN_ORGANIZATION_ID` in `.env` as described in the root README. Cypress receives those values from Compose. Without the password, only the invalid-credentials case runs.

The test container waits for the frontend health check, which depends on the backend health check and PostgreSQL readiness. Cypress dependencies and its binary are cached in named Docker volumes.

To type-check the E2E config and specs inside the running Cypress image:

```powershell
docker compose --profile e2e run --rm --entrypoint sh e2e -c "npm install --prefer-offline && npm run typecheck"
```
