# AtlasDesk architecture

AtlasDesk is a tenant-scoped modular monolith. The React client talks to a Spring REST API; the API owns authentication, authorization, validation, ticket workflows, SLA snapshots, and database access. Flyway applies versioned SQL migrations, and Hibernate validates the schema.

## UML class diagram

The diagram uses the JPA entities and relational foreign keys present in the source and migrations. `OrganizationSettings` is an API value record; the SLA values are columns on `Organization`, so it is not a separate persisted entity. `TicketHistoryEvent` stores foreign-key IDs as scalar fields rather than JPA object relations.

```mermaid
classDiagram
    class Organization {
        +Long id
        +String name
        +boolean requireApproval
        +int slaLowHours
        +int slaMediumHours
        +int slaHighHours
        +Instant createdAt
        +Instant updatedAt
    }
    class User {
        +Long id
        +String name
        +String email
        +String passwordHash
        +UserRole role
        +boolean active
    }
    class Asset {
        +Long id
        +String assetNumber
        +String name
        +String type
        +String description
        +AssetStatus status
    }
    class Ticket {
        +Long id
        +String title
        +String description
        +TicketPriority priority
        +TicketStatus status
        +Instant dueAt
        +Instant resolvedAt
        +Instant closedAt
        +long version
    }
    class TicketHistoryEvent {
        +Long id
        +Long organizationId
        +Long ticketId
        +Long actorId
        +String actorName
        +TicketHistoryEventType eventType
        +String description
        +Instant occurredAt
        +long ticketVersion
    }

    Organization "1" --> "0..*" User : organization_id
    Organization "1" --> "0..*" Asset : organization_id
    Organization "1" --> "0..*" Ticket : organization_id
    User "1" <-- "0..*" Ticket : requester_id
    User "0..1" <-- "0..*" Ticket : assigned_agent_id
    Asset "0..1" <-- "0..*" Ticket : asset_id
    Ticket "1" --> "0..*" TicketHistoryEvent : ticket_id
    Organization "1" --> "0..*" TicketHistoryEvent : organization_id
    User "1" <-- "0..*" TicketHistoryEvent : actor_id
```

## C4 container diagram

In C4, a container is an application or data store with a responsibility; the term does not mean a Docker container. The same browser workflows can target either of the two deployment arrangements below.

```mermaid
C4Container
    Person(visitor, "User", "Uses AtlasDesk from a browser")

    System_Boundary(atlasdesk, "AtlasDesk") {
        Container_Boundary(hosted, "Hosted free demonstration") {
            Container(pages, "Frontend", "React 18 / Vite static assets", "Cloudflare Pages; public HTTPS origin")
            Container(proxy, "API proxy", "Cloudflare Pages Function", "Forwards only /api/* to the configured HTTPS backend origin")
            Container(api, "Backend API", "Java 21 / Spring Boot 3.5", "Render Free Web Service; HttpSession and CSRF")
            ContainerDb(neon, "Database", "Neon PostgreSQL", "Remote TLS connection; small Hikari pool; Flyway migrations")
        }

        Container_Boundary(local, "Local development and tests") {
            Container(localPages, "Frontend", "React 18 / Vite dev server", "Docker Compose service frontend; localhost:5173")
            Container(localApi, "Backend API", "Java 21 / Spring Boot 3.5", "Docker Compose service backend; localhost:8080")
            ContainerDb(localDb, "Database", "PostgreSQL 16", "Docker Compose service db with a named persistent volume")
        }
    }

    Rel(visitor, pages, "Uses application", "HTTPS")
    Rel(pages, proxy, "Same-origin /api/* requests", "HTTPS")
    Rel(proxy, api, "Preserves HTTP method, path, query, cookies, CSRF and response", "HTTPS")
    Rel(api, neon, "Reads/writes tenant data; Flyway schema migration", "JDBC over TLS")
    Rel(visitor, localPages, "Uses application", "HTTP")
    Rel(localPages, localApi, "Vite proxies /api/* using the Compose service name", "HTTP / Docker network")
    Rel(localApi, localDb, "Reads/writes tenant data; Flyway schema migration", "JDBC / Docker network")
```

Locally, Compose runs all three application services on a private Docker network, publishes the frontend and API ports for developer access, and persists PostgreSQL and dependency caches in named volumes. In the hosted demonstration, Pages serves the static build and executes the narrow API proxy, Render runs the Java process on its assigned `PORT`, and Neon stores PostgreSQL data remotely. Compose is not a production orchestrator. The hosted backend uses process-local HTTP sessions, so users may have to sign in again after a restart.

See [deployment steps](deployment.md) for the manual provider settings and [decisions and limitations](decisions-and-limitations.md) for current product boundaries.
