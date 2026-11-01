# Modelo de domínio

Diagrama resumido das entidades JPA e associações existentes. `TicketHistoryEvent` registra IDs e um snapshot do nome do ator; não possui associação JPA direta ao chamado.

```mermaid
classDiagram
  class Organization {
    Long id
    String name
    boolean requireApproval
    int slaLowHours
    int slaMediumHours
    int slaHighHours
  }
  class User {
    Long id
    String name
    String email
    String passwordHash
    UserRole role
    boolean active
  }
  class Asset {
    Long id
    String assetNumber
    String name
    String type
    AssetStatus status
  }
  class Ticket {
    Long id
    String title
    TicketPriority priority
    TicketStatus status
    Instant dueAt
    Instant resolvedAt
    Instant closedAt
    long version
  }
  class TicketHistoryEvent {
    Long id
    Long organizationId
    Long ticketId
    Long actorId
    String actorName
    TicketHistoryEventType eventType
    Instant occurredAt
  }
  class UserRole {
    <<enumeration>>
    ADMIN
    AGENT
    REQUESTER
  }
  class TicketStatus {
    <<enumeration>>
    OPEN
    IN_PROGRESS
    PENDING_APPROVAL
    RESOLVED
    CLOSED
  }
  class TicketPriority {
    <<enumeration>>
    LOW
    MEDIUM
    HIGH
  }
  class AssetStatus {
    <<enumeration>>
    ACTIVE
    INACTIVE
  }
  class TicketHistoryEventType {
    <<enumeration>>
    CREATED
    ASSIGNED
    STARTED
    RESOLUTION_REQUESTED
    RESOLVED
    APPROVED
    REJECTED
    CLOSED
  }

  Organization "1" --> "0..*" User : organization
  Organization "1" --> "0..*" Asset : organization
  Organization "1" --> "0..*" Ticket : organization
  User "1" --> "0..*" Ticket : requester
  User "0..1" --> "0..*" Ticket : assignedAgent
  Asset "0..1" --> "0..*" Ticket : asset
  Ticket ..> TicketHistoryEvent : history by IDs
  User --> UserRole : role
  Ticket --> TicketStatus : status
  Ticket --> TicketPriority : priority
  TicketHistoryEvent --> TicketHistoryEventType : eventType
  Asset --> AssetStatus : status
```

SLA fica em `Organization` como horas por prioridade; cada `Ticket` guarda `dueAt` como snapshot calculado na criação. Aprovação é representada pelo estado e fluxo do chamado, não por uma entidade separada.
