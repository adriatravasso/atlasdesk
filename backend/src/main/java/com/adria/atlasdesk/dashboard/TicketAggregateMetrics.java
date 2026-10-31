package com.adria.atlasdesk.dashboard;

public record TicketAggregateMetrics(
        Long totalTickets,
        Long openTickets,
        Long inProgressTickets,
        Long pendingApprovalTickets,
        Long resolvedTickets,
        Long closedTickets,
        Long overdueTickets,
        Long lowPriorityTickets,
        Long mediumPriorityTickets,
        Long highPriorityTickets) {
}
