# Permission matrix

The API enforces these permissions in the service layer. Every organization-owned query and write is scoped to the authenticated session's `organizationId`; an organization or resource ID supplied by a client does not grant access to another tenant. Resources outside the caller's organization are treated as not found.

| Functionality | ADMIN | AGENT | REQUESTER |
| --- | --- | --- | --- |
| Session profile (`/api/auth/me`) | Read own profile | Read own profile | Read own profile |
| Dashboard (`/api/dashboard`) | Organization ticket metrics and active asset count | Organization ticket metrics and active asset count | Own ticket metrics and organization active asset count |
| Ticket list, detail, and history | Read organization tickets | Read organization tickets | Read own tickets |
| Ticket creation | Create as self | Create as self | Create as self |
| Assignment | Assign to an active same-organization agent | Assign to an active same-organization agent | No access |
| Ticket workflow | Start, resolve, approve/reject pending resolution, and close | Start/resolve tickets assigned to self | Close own resolved tickets |
| Assets (`/api/assets`) | Read, create, edit, activate/inactivate | Read, create, edit, activate/inactivate | Read active assets with the fields needed to associate one with a ticket |
| User directory (`/api/users`) | List, create, edit, activate/deactivate users in own organization | No access | No directory access; own profile only |
| SLA settings (`/api/settings`) | Read/update organization values | Read effective values | Read effective values |

## Workflow details

Ticket transitions are validated by the backend. ADMIN can perform administrative transitions; AGENT can start or resolve only a ticket assigned to that agent; REQUESTER can close only their own resolved ticket. When organization approval is enabled, an agent's `RESOLVE` request creates `PENDING_APPROVAL`; only an ADMIN in the same organization can approve or reject it. Resolution approval is optional and configured per organization.

Dashboard ticket counts are organization-wide for ADMIN and AGENT, and requester-scoped for REQUESTER. Active asset count is organization-wide for all three roles. The E2E suite verifies unauthorized roles and cross-organization boundaries against a separate test database.

The current product does not expose ticket comments or attachments. Do not infer access for unimplemented routes from this matrix; update this document together with any future API authorization change.

Anonymous API requests return HTTP 401; authenticated users without a role permission receive HTTP 403. A resource outside the organization is indistinguishable from a missing resource and returns HTTP 404.
