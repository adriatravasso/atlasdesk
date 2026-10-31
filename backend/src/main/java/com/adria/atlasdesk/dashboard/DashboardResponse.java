package com.adria.atlasdesk.dashboard;

public record DashboardResponse(
        long totalTickets,
        long openTickets,
        long inProgressTickets,
        long pendingApprovalTickets,
        long resolvedTickets,
        long closedTickets,
        long overdueTickets,
        PriorityDistribution priorityDistribution,
        long activeAssets) {

    public record PriorityDistribution(long low, long medium, long high) {
    }
}
