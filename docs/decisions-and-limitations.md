# Decisions and current limitations

This note records decisions that affect the existing behavior. It is not a roadmap; update it when a requirement changes.

## Decisions

- **Modular monolith and REST API.** The API is organized under `com.adria.atlasdesk` by implemented domain. The backend owns authorization, validation, tenant scoping, workflow transitions, and SLA calculation.
- **Organization scope comes from the session.** Organization IDs in request bodies or query parameters do not select tenant scope. Repository queries include the authenticated organization where applicable.
- **Browser authentication uses a server session and CSRF protection.** Session identifiers remain in HttpOnly cookies; the application does not store credentials or session tokens in browser storage.
- **Flyway owns database evolution.** Versioned SQL migrations are applied on backend startup, and JPA validates the migrated schema.
- **Ticket deadlines are snapshots.** The `dueAt` value is calculated from the organization SLA when a ticket is created. Later priority or SLA changes do not rewrite existing deadlines.
- **Approval is an organization setting.** When enabled, agent resolution requests wait in `PENDING_APPROVAL` for an ADMIN. Workflow rules are enforced by the backend.

## Limitations

- No email/notification delivery, ticket comment thread, file attachment, password recovery, or SSO flow is implemented.
- The dashboard provides current aggregate counts and priority distribution, not historical reporting or exports.
- SLA durations are elapsed hours, not business hours, and there is no background job or escalation notification.
- Migration V6 records new history events and adds a creation event for existing tickets. It does not reconstruct earlier assignment or status-change history.
- The hosted demonstration uses Cloudflare Pages, Render Free, and Neon Free; it has no business backup, external monitoring, or operational incident process. See [deployment details](deployment.md).
- E2E demo accounts belong exclusively to the E2E profile and its isolated database. The hosted demo uses a separate synthetic `REQUESTER` account with no administrative permissions; its password is configured as a Render secret and shared out of band.
- No public ADMIN credentials are published. The hosted demo identity is scoped to its own organization.

## Release gate

Do not create a stable release tag until the deployed build has passed validation, the remaining limitations have been reviewed, and a human has explicitly approved the release.
