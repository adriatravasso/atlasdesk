# AtlasDesk E2E tests

The Cypress project is run in the `e2e` Docker Compose profile. It uses the frontend's landing page and its real `/api/health` proxy; no local Node.js, npm, or Cypress installation is needed.

From the repository root, start the application and run the smoke test:

```powershell
docker compose up -d --build db backend frontend
docker compose --profile e2e run --rm e2e
```

The test container waits for the frontend health check, which depends on the backend health check and PostgreSQL readiness. Cypress dependencies and its binary are cached in named Docker volumes.

To type-check the E2E config and specs inside the running Cypress image:

```powershell
docker compose --profile e2e run --rm --entrypoint sh e2e -c "npm install --prefer-offline && npm run typecheck"
```
