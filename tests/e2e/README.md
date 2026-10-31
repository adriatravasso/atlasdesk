# AtlasDesk E2E tests

The Cypress project is run in the `e2e` Docker Compose profile. It verifies invalid credentials and, when configured, real login, session reload, protected user access, ticket creation/list/detail, and logout. No local Node.js, npm, or Cypress installation is needed.

From the repository root, start the application and run the smoke test:

```powershell
docker compose up -d --build db backend frontend
docker compose --profile e2e run --rm e2e
```

For the real-session case, set `DEV_ADMIN_PASSWORD` and `DEV_ADMIN_ORGANIZATION_ID` in `.env` as described in the root README. Cypress receives those values from Compose. Without the password, only the invalid-credentials case runs. To include the user-directory UI flow, also set `ATL0008_E2E_USER_EMAIL` to a new, unique local email; Cypress creates, edits, and deactivates that account. Set `ATL0010_E2E_ASSET_NUMBER` to a new, unique local asset number to exercise asset creation, editing, inactivation, and filtering at desktop and mobile viewport widths. Set `ATL0012_E2E_TICKET_TITLE` to a unique title to create a real ticket and verify its detail view and list entry.

The test container waits for the frontend health check, which depends on the backend health check and PostgreSQL readiness. Cypress dependencies and its binary are cached in named Docker volumes.

To type-check the E2E config and specs inside the running Cypress image:

```powershell
docker compose --profile e2e run --rm --entrypoint sh e2e -c "npm install --prefer-offline && npm run typecheck"
```
