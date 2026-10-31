# Permission matrix

This matrix defines the default authorization policy for AtlasDesk features. All organization-owned records must be queried and changed using the organization ID from the authenticated session. IDs or organization IDs supplied by a client never grant access. A resource outside the caller's organization is treated as not found.

| Functionality | ADMIN | AGENT | REQUESTER |
| --- | --- | --- | --- |
| Own profile (`/api/auth/me`) | Read | Read | Read |
| User directory in own organization | Read, deactivate | No access | No access; own profile only |
| Organization settings and membership | Read, manage | Read | No access |
| Tickets in own organization | Read, create, assign, update, transition | Read, create, assign, update, transition | Create; read and comment on own tickets |
| Assets in own organization | Read, create, update, inactivate | Read, create, update, inactivate | Read minimal details for ticket association |
| SLA and approval settings | Read, manage | Read effective values | No access |
| Operational reports | Read | Read | No access |

## Enforcement status

The current API exposes health and authentication routes only. `UserDirectoryService` demonstrates the enforced policy for the existing user resource: only ADMIN can list, read, or deactivate users, and every query is scoped to the authenticated organization. Ticket, asset, settings, and report permissions must be enforced in their backend services/controllers when those features are added; this matrix should be reviewed with each feature.

Role denial returns HTTP 403. A user outside the current organization is indistinguishable from a missing user and returns HTTP 404. Anonymous API requests return HTTP 401.
