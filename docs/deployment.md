# Free demonstration deployment

This guide prepares the existing private repository for a non-production demonstration. It does not publish the application. Keep real user data, credentials, and production workloads out of the free environment.

## 1. Create the Neon database

1. Create a Neon project using PostgreSQL 16 and select a region near the Render service.
2. Create an application role and an `atlasdesk` database.
3. Copy the **pooled** connection endpoint and credentials from Neon. Configure the JDBC URL using TLS, for example `jdbc:postgresql://<pooled-endpoint>/<database>?sslmode=require`.
4. Keep the database URL, username, and password in Render environment settings. Do not place them in the frontend or source control.

The production profile runs Flyway before serving requests, uses `ddl-auto=validate`, and caps the Hikari pool at three connections by default. The database must be reachable from Render. No local files are used for persistence.

## 2. Deploy the backend on Render

1. In Render, create a **Free Web Service** linked to the private `adriatravasso/atlasdesk` repository and deploy branch `main` after the ATL-0025 changes are reviewed and integrated.
2. Use repository root as the build context and `backend/Dockerfile` as the Dockerfile path. Select the Free compute plan and set `/api/health` as the health-check path.
3. Set these environment variables. Add the password values using Render's secret input; never commit them.

   | Variable | Value |
   | --- | --- |
   | `SPRING_PROFILES_ACTIVE` | `prod` |
   | `DB_URL` | `jdbc:postgresql://<pooled-endpoint>/<database>?sslmode=require` |
   | `DB_USERNAME` | Neon application role |
   | `DB_PASSWORD` | Neon application role password (secret) |
   | `DB_POOL_MAX` | `3` |
   | `SESSION_COOKIE_SECURE` | `true` |
   | `DEMO_ADMIN_PASSWORD` | A unique, strong synthetic demo password (secret) |
   | `DEMO_ADMIN_EMAIL` | Optional; defaults to `atlasdesk-demo@example.invalid` |
   | `DEMO_ORGANIZATION_NAME` | Optional; defaults to `AtlasDesk Public Demo` |

Render supplies `PORT`; the application binds to it. The production data seeder creates only the synthetic demo organization and ADMIN when the password secret is set, and does not overwrite an existing user. No public registration route is enabled. Keep the admin password private; share access only with intended reviewers. Remove or rotate the secret if the demo is no longer needed.

After the first successful start, check Render logs for Flyway completing and `/api/health` passing. The `DB_URL` format and password are stored only in the backend configuration, not in Vite variables or the Pages bundle.

## 3. Publish the frontend on Cloudflare Pages

1. Create a Pages project connected to the private repository and set production branch to `main`.
2. Set **Root directory** to `frontend`, **Build command** to `npm run build`, and **Build output directory** to `dist`.
3. Add a runtime Pages Function variable named `ATLASDESK_API_ORIGIN` with the Render HTTPS origin, such as `https://<service>.onrender.com`. Do not prefix this with `VITE_`; it is read by the server-side Function, not embedded in JavaScript.
4. Deploy and use the generated `https://<project>.pages.dev` address.

The checked-in `frontend/functions/api/[[path]].js` only handles `/api/*`, derives its target solely from the configured HTTPS origin, preserves request and response details required by the existing session and CSRF flow, and returns a gateway error when the backend is unavailable. `frontend/public/_routes.json` keeps static assets out of Function invocation routes. Local development continues to use the existing Vite proxy to the Compose `backend` service.

## 4. Validate the published setup

From the Pages URL:

1. Confirm `/api/health` returns `UP` after the Render service starts.
2. Sign in with the generated organization ID, configured `DEMO_ADMIN_EMAIL`, and the password configured privately in Render.
3. Create and edit a synthetic user, open a ticket, and confirm the session cookie remains `HttpOnly`, `Secure`, and `SameSite=Lax` in browser developer tools.
4. Confirm mutating requests carry the existing CSRF header and that a user without ADMIN permission receives the existing denial.
5. Test logout, then allow Render to idle and confirm the Pages frontend stays available while the status notice reports startup and then readiness.

Do not advertise deployment as complete until these checks pass against actual provider deployments. Jenkins and Cypress still run through the existing Docker Compose pipeline.

## Free-tier limitations

- Render documents 0.1 CPU and 512 MB RAM for a Free web service. It suspends a free service after 15 minutes without inbound traffic; the next request can take about a minute to wake it. Instance hours and bandwidth/build quotas also apply.
- Render service filesystems are ephemeral. HTTP sessions live in backend memory and are lost on restart or spin-down. Neon is the database persistence layer; no real user data belongs there.
- Neon Free quotas and behavior depend on its current plan and may change. Review the Neon dashboard for current compute, storage, and connection limits before deployment. Keep the pool small and avoid long-running transactions.
- Cloudflare Pages serves static assets independently of Render availability. Pages Functions have their own free-plan request allowance; `/api/*` traffic consumes function invocations.
- These free services have no availability guarantee or commercial SLA. Their policies, quotas, and features can change. The environment is for demonstration only, not commercial production.

Provider references: [Cloudflare Pages build settings](https://developers.cloudflare.com/pages/configuration/build-configuration/), [Pages Function routing and invocation rules](https://developers.cloudflare.com/pages/functions/routing/), [Render free service limits](https://render.com/docs/free), [Render port binding](https://render.com/docs/web-services), and [Neon documentation](https://neon.tech/docs/).
