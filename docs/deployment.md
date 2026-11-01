# Hosted demonstration

AtlasDesk uses free-tier services for a public, non-commercial demonstration. Do not store personal or business data in this environment.

## Current deployment

| Component | Provider and resource | Configuration |
| --- | --- | --- |
| Frontend | [Cloudflare Pages — atlasdesk](https://atlasdesk.pages.dev) | Private repository `adriatravasso/atlasdesk`, production branch `main`, root `frontend`, build `npm ci && npm run build`, output `dist` |
| API | [Render — atlasdesk-api](https://atlasdesk-api.onrender.com) | Free Docker Web Service in Ohio, Dockerfile `backend/Dockerfile`, production profile, health endpoint `/api/health` |
| Database | Neon project `atlasdesk`, branch `production`, database `atlasdeskdb` | PostgreSQL 18, TLS JDBC connection, dedicated `atlasdesk_app` role, Flyway migrations through V6 |

Cloudflare Pages builds the frontend from `main` and serves static files at `https://atlasdesk.pages.dev`. Its `/api/*` Pages Function forwards requests to `https://atlasdesk-api.onrender.com`. The browser never receives the database URL or password. The backend health endpoint returned HTTP 200 after the first Render deploy and all six Flyway migrations completed successfully against PostgreSQL 18.6.

## Provider settings

The Cloudflare Pages project is connected to the private GitHub repository and production branch `main`. Its production settings include `NODE_VERSION=22.13.0` and the Pages Function variable `ATLASDESK_API_ORIGIN=https://atlasdesk-api.onrender.com`.

Render receives `SPRING_PROFILES_ACTIVE=prod`, the Neon JDBC URL and credentials as service environment variables, `DB_POOL_MAX=3`, and `SESSION_COOKIE_SECURE=true`. Render supplies the listening `PORT`. Keep database credentials only in Render's secret environment. Do not configure `DEMO_ADMIN_PASSWORD` for public access or publish any administrator credentials.

Neon currently runs PostgreSQL 18.6 in `aws-us-east-2`. The `production` branch was empty of AtlasDesk application tables before the first deployment. The dedicated application role has database connect and `public` schema usage/create privileges required by Flyway and the application. Flyway applies schema changes at backend startup; Hibernate validates the resulting schema.

## Authentication and data

No public demo account is configured. Visitors can reach the frontend, but a valid application user is required to sign in. Do not use E2E credentials or local seeded accounts in the hosted database. Do not add a public ADMIN password. If a restricted demo identity is introduced later, it must be isolated to synthetic data and unable to administer the platform or access another organization.

The API keeps session and CSRF protection enabled. Session cookies use HTTPS settings, and organization scope comes from the authenticated backend session. Render's filesystem is ephemeral and in-memory sessions are lost when the free service restarts or wakes from idle.

## Limits

- Render Free provides limited CPU and memory, suspends after inactivity, and can take about a minute to wake. It has no production SLA.
- Neon Free has storage, compute, branch, and connection limits; keep the Hikari pool small and monitor the current plan quotas.
- Cloudflare Pages serves static assets separately from Pages Functions. API calls use Functions and count toward the applicable free request limits.
- None of these services provides a business backup, incident response, or production availability guarantee for this demo.

Provider quotas and behavior can change. Recheck [Render Free limits](https://render.com/docs/free), the [Neon plan](https://neon.com/pricing), and [Cloudflare Pages limits](https://developers.cloudflare.com/pages/platform/limits/) before materially changing the deployment.
