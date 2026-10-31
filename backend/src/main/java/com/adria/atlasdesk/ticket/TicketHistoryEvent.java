package com.adria.atlasdesk.ticket;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ticket_history_events")
public class TicketHistoryEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(name = "actor_name", nullable = false, length = 160)
    private String actorName;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private TicketHistoryEventType eventType;

    @Column(nullable = false, length = 240)
    private String description;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "ticket_version", nullable = false)
    private long ticketVersion;

    protected TicketHistoryEvent() {
    }

    public TicketHistoryEvent(Long organizationId, Long ticketId, Long actorId, String actorName,
            TicketHistoryEventType eventType, String description, Instant occurredAt, long ticketVersion) {
        this.organizationId = organizationId;
        this.ticketId = ticketId;
        this.actorId = actorId;
        this.actorName = actorName;
        this.eventType = eventType;
        this.description = description;
        this.occurredAt = occurredAt;
        this.ticketVersion = ticketVersion;
    }
}
