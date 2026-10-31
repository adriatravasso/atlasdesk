package com.adria.atlasdesk.ticket;

import java.time.Instant;

public record TicketSummary(Long id, Long organizationId, String title, String description,
        Long requesterId, String requesterName, Long assignedAgentId, String assignedAgentName,
        Long assetId, String assetNumber, TicketPriority priority, TicketStatus status,
        Instant createdAt, Instant updatedAt, Instant dueAt, Instant resolvedAt, Instant closedAt,
        boolean overdue) {

    public TicketSummary(Long id, Long organizationId, String title, String description,
            Long requesterId, String requesterName, Long assignedAgentId, String assignedAgentName,
            Long assetId, String assetNumber, TicketPriority priority, TicketStatus status,
            Instant createdAt, Instant updatedAt, Instant dueAt, Instant resolvedAt, Instant closedAt) {
        this(id, organizationId, title, description, requesterId, requesterName, assignedAgentId,
                assignedAgentName, assetId, assetNumber, priority, status, createdAt, updatedAt,
                dueAt, resolvedAt, closedAt, false);
    }

    public TicketSummary withOverdue(Instant now) {
        boolean late = status != TicketStatus.RESOLVED && status != TicketStatus.CLOSED
                && dueAt != null && dueAt.isBefore(now);
        return new TicketSummary(id, organizationId, title, description, requesterId, requesterName,
                assignedAgentId, assignedAgentName, assetId, assetNumber, priority, status,
                createdAt, updatedAt, dueAt, resolvedAt, closedAt, late);
    }
}
