# Free demonstration deployment

AtlasDesk is prepared for a public, non-commercial demonstration using the existing private repository. Deployments must come from `main` after the ATL-0028 change is reviewed and integrated. Keep real user data, credentials, and production workloads out of the free environment.

## Existing resource inventory

- Neon project `atlasdesk` belongs to Adria's account, runs PostgreSQL 18 in `aws-us-east-2`, and is on the Free plan. Its `production` branch is ready, with database `atlasdeskdb`; the initial inspection found no application tables and only the project owner role. The endpoint was idle at inspection time. Recheck its plan, usage, and data before applying migrations.
- Render workspace `My Workspace` belongs to `adria.travasso.dev@gmail.com`; no AtlasDesk service existed at inventory time. Create only `atlasdesk-api` as a Free Docker Web Service.
- Adria's Cloudflare account had no Pages project named `atlasdesk` at inventory time. Recheck before creating it; configure production from `main`.

## PostgreSQL compatibility and database setup

Neon currently runs PostgreSQL 18, while local development uses PostgreSQL 16. Before touching the Neon production branch, run the backend tests and all Flyway migrations against isolated PostgreSQL 18 in Docker Compose. Do not use the Neon production database as a migration test environment.

Use the existing `atlasdesk` Neon project and `production` branch; do not create a duplicate project, reset the branch, or copy development data. Create a dedicated application role with only database connect and schema/table privileges required by Flyway and the app. Use the direct Neon endpoint with a TLS JDBC URL such as `jdbc:postgresql://<endpoint>/atlasdeskdb?sslmode=require`; the inspected branch had connection pooling disabled. Store the username and password only in Render's secret environment. Check Neon Free limits in its dashboard before publishing.

The production profile uses Flyway at startup, `ddl-auto=validate`, a three-connection Hikari pool by default, and Render's `PORT`. It keeps session and CSRF cookies HttpOnly, Secure, and SameSite=Lax. Render's free web service has 512 MB RAM and 0.1 CPU, can sleep after 15 minutes without inbound traffic, and typically needs about one minute to wake; the frontend status banner accounts for that delay. The JVM's container-aware memory ceiling stays below the full 512 MB limit.

## Backend on Render

Create `atlasdesk-api` in the confirmed `My Workspace` as a Free Web Service connected to `adriatravasso/atlasdesk`, production branch `main`, runtime Docker. Use repository root as the build context, `backend/Dockerfile` as the Dockerfile path, and `/api/health` as the health-check path. Do not use the full Compose stack in production.

Set only the environment variables needed by the production profile:

| Variable | Value |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_URL` | Neon JDBC URL with `sslmode=require` |
| `DB_USERNAME` | Dedicated Neon application role |
| `DB_PASSWORD` | Secret for that role |
| `DB_POOL_MAX` | `3` |
| `SESSION_COOKIE_SECURE` | `true` |
| `DEMO_ADMIN_PASSWORD` | Omit unless a private reviewer account is explicitly needed; never publish this secret |

Render supplies `PORT`. Confirm the service is on the Free plan, not a paid compute plan, before creating it. Inspect the build, startup logs, Flyway result, health endpoint, and database connection after deployment.

## Frontend on Cloudflare Pages

Create or reuse Pages project `atlasdesk` in Adria's confirmed Cloudflare account, connected to the private repository `adriatravasso/atlasdesk` and production branch `main`. Configure:

- Root directory: `frontend`
- Build command: `npm ci && npm run build`
- Build output directory: `dist`
- `NODE_VERSION`: `22.13.0`
- Runtime Pages Function variable `ATLASDESK_API_ORIGIN`: HTTPS origin of the Render service, with no path or credentials

`frontend/functions/api/[[path]].js` only proxies `/api` requests to that configured HTTPS origin; the browser cannot choose the destination. It carries the existing session and CSRF headers and does not add app authentication or business logic. `frontend/public/_routes.json` keeps static assets outside the Function route. Cloudflare Pages serves static assets even while Render is starting.

## Validation after deployment

From the published HTTPS Pages URL, verify the SPA entry point and direct internal routes, static assets, and `/api/health`. Confirm Render completed Flyway and remains healthy after the Neon database wakes. Test login, logout, session cookies, CSRF on a mutation, role authorization, and organization isolation using only synthetic data. Verify dashboard, tickets, assets, user administration, SLAs, and approvals. Confirm the UI displays `v1.0.0`.

Do not create a public ADMIN login or publish a password. No demo account is seeded unless a private reviewer credential is configured in Render. Do not copy local or Cypress accounts into the demo database. The seeded demo data, if enabled, is synthetic and belongs to one isolated organization. Document a manual reset procedure; do not add a paid scheduled job or external monitor.

## Free-tier limits

- Render Free Web Service: 512 MB RAM, 0.1 CPU, sleeps after 15 idle minutes, ephemeral filesystem, limited monthly included instance hours, bandwidth, and build minutes. It has no availability guarantee; prevent paid upgrades and watch usage.
- Neon Free: verify current account quotas before release. At the time this guide was updated, the plan included 100 projects, 1 GB storage per project, and per-project compute/branch limits. The existing project also autoscales and can suspend to zero.
- Cloudflare Pages Free: static asset delivery is separate from Function invocations. Pages Functions consume the Workers free request quota, and builds have monthly/concurrency limits.

Provider policies change. Recheck the current [Render Free plan](https://render.com/docs/free) and [compute plans](https://render.com/docs/compute-plans), [Neon Free plan](https://neon.com/pricing) and [latest plan changes](https://neon.com/blog/category/changelog), and [Cloudflare Pages limits](https://developers.cloudflare.com/pages/platform/limits/) before publishing. This demonstration has no commercial SLA and is not for production business data.
