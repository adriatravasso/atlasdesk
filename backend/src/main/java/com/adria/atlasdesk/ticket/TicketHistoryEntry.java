package com.adria.atlasdesk.ticket;

import java.time.Instant;

public record TicketHistoryEntry(Long id, TicketHistoryEventType eventType, Long actorId,
        String actorName, String description, Instant occurredAt) {
}
