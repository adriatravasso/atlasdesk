# Decisions and current limitations

This note records decisions that affect the existing behavior. It is not a roadmap; update it when a requirement changes.

## Decisions

- **Modular monolith and REST API.** The API is organized under `com.adria.atlasdesk` by implemented domain. The backend owns authorization, validation, tenant scoping, workflow transitions, and SLA calculation.
- **Organization scope comes from the session.** Organization IDs in request bodies or query parameters do not select tenant scope. Repository queries include the authenticated organization where applicable.
- **Browser authentication uses a server session and CSRF protection.** Session identifiers remain in HttpOnly cookies; the application does not store credentials or session tokens in browser storage.
- **Login attempts have a local throttle.** Each organization/email pair is limited to 10 requests per minute per backend instance and receives HTTP 429 with `Retry-After` when exceeded. The counter is in memory; scaling to multiple backend instances requires a shared limiter.
- **Flyway owns database evolution.** Versioned SQL migrations are applied on backend startup, and JPA validates the migrated schema.
- **Ticket deadlines are snapshots.** The `dueAt` value is calculated from the organization SLA when a ticket is created. Later priority or SLA changes do not rewrite existing deadlines.
- **Approval is an organization setting.** When enabled, agent resolution requests wait in `PENDING_APPROVAL` for an ADMIN. Workflow rules are enforced by the backend.

## Limitations

- No email/notification delivery, ticket comment thread, file attachment, password recovery, or SSO flow is implemented.
- The dashboard provides current aggregate counts and priority distribution, not historical reporting or exports.
- SLA durations are elapsed hours, not business hours, and there is no background job or escalation notification.
- Migration V6 records new history events and adds a creation event for existing tickets. It does not reconstruct earlier assignment or status-change history.
- The hosted demonstration uses Cloudflare Pages, Render Free, and Neon Free; it has no business backup, external monitoring, or operational incident process. See [deployment details](deployment.md).
- E2E demo accounts belong exclusively to the E2E profile and its isolated database. The hosted public account is a synthetic `AGENT`; startup restoration is restricted to the named demo organization and runs only with `DEMO_SEED_ENABLED=true`.
- The public AGENT can create and change demo tickets and assets in its own organization. Visitor changes persist until the backend restarts; the shared demo has no per-visitor isolation. No public ADMIN credentials are provided.

## Release gate

Do not create a stable release tag until the deployed build has passed validation, the remaining limitations have been reviewed, and a human has explicitly approved the release.
