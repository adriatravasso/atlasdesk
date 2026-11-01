# AtlasDesk

AtlasDesk is a multi-tenant IT service desk for support tickets, technology assets, service-level deadlines, and resolution approvals. The backend enforces organization-scoped access and owns validation, authorization, and workflow rules.

## Screenshots

Captured from the running application using synthetic data in the isolated Cypress environment.

| Organization dashboard | Asset inventory |
| --- | --- |
| ![AtlasDesk organization dashboard](docs/screenshots/portfolio-screenshots.cy.ts/portfolio-dashboard.png) | ![AtlasDesk asset inventory](docs/screenshots/portfolio-screenshots.cy.ts/portfolio-assets.png) |

| Ticket workflow | Ticket history |
| --- | --- |
| ![AtlasDesk ticket workflow](docs/screenshots/portfolio-screenshots.cy.ts/portfolio-ticket-workflow.png) | ![AtlasDesk ticket history](docs/screenshots/portfolio-screenshots.cy.ts/portfolio-ticket-history.png) |

| Organization settings | User administration |
| --- | --- |
| ![AtlasDesk organization settings](docs/screenshots/portfolio-screenshots.cy.ts/portfolio-settings.png) | ![AtlasDesk user administration](docs/screenshots/portfolio-screenshots.cy.ts/portfolio-users.png) |

## Features

- Organization-scoped login, session restoration, logout, and role-based navigation.
- Ticket search, assignment, status transitions, SLA deadlines, optional resolution approval, and event history.
- Organization asset inventory with create, update, activation, and search.
- Dashboard metrics, organization SLA settings, and administrator-managed users.
- CSRF protection and server-side authorization with tenant scope derived from the authenticated session.

## Stack

| Area | Technologies |
| --- | --- |
| API | Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, Maven Wrapper |
| Web | React 18.3.1, TypeScript strict mode, Vite, React Router 7, native CSS |
| Data | PostgreSQL 16 locally, Neon PostgreSQL 18 for the hosted demo, Flyway |
| Validation | Docker Compose, JUnit 5, Cypress 15, Jenkins |

## Run locally

Requires Git and Docker Compose v2. Application runtimes and dependencies run inside containers.

```powershell
Copy-Item .env.example .env
docker compose --profile e2e config --quiet
docker compose up -d --build --wait db backend frontend
```

Open <http://localhost:5173>. The API health endpoint is <http://localhost:8080/api/health>. Full setup, local demo data, E2E accounts, validation, and shutdown instructions are in [Development](docs/development.md).

## Hosted demonstration

The public frontend is served by [Cloudflare Pages](https://atlasdesk.pages.dev), with API requests proxied to the [Render backend](https://atlasdesk-api.onrender.com/api/health). The database is hosted on Neon. This is a free-tier demonstration; sign in with the restricted demo requester account described out of band. See [deployment details and limits](docs/deployment.md).

## Documentation

- [Development and validation](docs/development.md)
- [Architecture](docs/architecture.md)
- [Permissions](docs/permissions.md)
- [Deployment](docs/deployment.md)
- [Decisions and limitations](docs/decisions-and-limitations.md)

The repository is private. Do not create a stable release tag until the deployed build has passed validation and received explicit release approval.
