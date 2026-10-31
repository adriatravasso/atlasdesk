package com.adria.atlasdesk.dashboard;

import java.time.Clock;
import com.adria.atlasdesk.asset.AssetRepository;
import com.adria.atlasdesk.asset.AssetStatus;
import com.adria.atlasdesk.security.AuthenticatedUser;
import com.adria.atlasdesk.security.CurrentUserContext;
import com.adria.atlasdesk.ticket.TicketRepository;
import com.adria.atlasdesk.user.UserRole;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final TicketRepository tickets;
    private final AssetRepository assets;
    private final CurrentUserContext currentUser;
    private final Clock clock;

    public DashboardService(TicketRepository tickets, AssetRepository assets,
            CurrentUserContext currentUser, Clock clock) {
        this.tickets = tickets;
        this.assets = assets;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT', 'REQUESTER')")
    public DashboardResponse getMetrics() {
        AuthenticatedUser actor = currentUser.requireUser();
        boolean requesterOnly = actor.role() == UserRole.REQUESTER;
        TicketAggregateMetrics metrics = tickets.aggregateDashboardMetrics(actor.organizationId(), actor.id(),
                requesterOnly, clock.instant());
        long activeAssets = assets.countByOrganizationIdAndStatus(actor.organizationId(), AssetStatus.ACTIVE);

        return new DashboardResponse(
                metrics.totalTickets(),
                metrics.openTickets(),
                metrics.inProgressTickets(),
                metrics.pendingApprovalTickets(),
                metrics.resolvedTickets(),
                metrics.closedTickets(),
                metrics.overdueTickets(),
                new DashboardResponse.PriorityDistribution(metrics.lowPriorityTickets(),
                        metrics.mediumPriorityTickets(), metrics.highPriorityTickets()),
                activeAssets);
    }
}
