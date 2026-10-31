package com.adria.atlasdesk.ticket;

import java.time.Instant;

public record TicketSummary(Long id, Long organizationId, String title, String description,
        Long requesterId, String requesterName, Long assignedAgentId, String assignedAgentName,
        Long assetId, String assetNumber, TicketPriority priority, TicketStatus status,
        Instant createdAt, Instant updatedAt, Instant dueAt, Instant resolvedAt, Instant closedAt) {
}
