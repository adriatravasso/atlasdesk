package com.adria.atlasdesk.ticket;

import java.time.Instant;
import java.util.Optional;
import com.adria.atlasdesk.dashboard.TicketAggregateMetrics;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    @Query("""
            select new com.adria.atlasdesk.dashboard.TicketAggregateMetrics(
                count(ticket),
                count(case when ticket.status = com.adria.atlasdesk.ticket.TicketStatus.OPEN then ticket.id end),
                count(case when ticket.status = com.adria.atlasdesk.ticket.TicketStatus.IN_PROGRESS then ticket.id end),
                count(case when ticket.status = com.adria.atlasdesk.ticket.TicketStatus.PENDING_APPROVAL then ticket.id end),
                count(case when ticket.status = com.adria.atlasdesk.ticket.TicketStatus.RESOLVED then ticket.id end),
                count(case when ticket.status = com.adria.atlasdesk.ticket.TicketStatus.CLOSED then ticket.id end),
                count(case when ticket.status in (
                    com.adria.atlasdesk.ticket.TicketStatus.OPEN,
                    com.adria.atlasdesk.ticket.TicketStatus.IN_PROGRESS,
                    com.adria.atlasdesk.ticket.TicketStatus.PENDING_APPROVAL
                ) and ticket.dueAt < :asOf then ticket.id end),
                count(case when ticket.priority = com.adria.atlasdesk.ticket.TicketPriority.LOW then ticket.id end),
                count(case when ticket.priority = com.adria.atlasdesk.ticket.TicketPriority.MEDIUM then ticket.id end),
                count(case when ticket.priority = com.adria.atlasdesk.ticket.TicketPriority.HIGH then ticket.id end)
            )
            from Ticket ticket
            where ticket.organization.id = :organizationId
              and (:requesterOnly = false or ticket.requester.id = :requesterId)
            """)
    TicketAggregateMetrics aggregateDashboardMetrics(@Param("organizationId") Long organizationId,
            @Param("requesterId") Long requesterId, @Param("requesterOnly") boolean requesterOnly,
            @Param("asOf") Instant asOf);

    Optional<Ticket> findByIdAndOrganizationId(Long id, Long organizationId);

    Optional<Ticket> findByOrganizationIdAndTitle(Long organizationId, String title);

    @Query(value = """
            select new com.adria.atlasdesk.ticket.TicketSummary(
                ticket.id, ticket.organization.id, ticket.title, ticket.description,
                requester.id, requester.name, assignedAgent.id, assignedAgent.name,
                asset.id, asset.assetNumber, ticket.priority, ticket.status,
                ticket.createdAt, ticket.updatedAt, ticket.dueAt, ticket.resolvedAt, ticket.closedAt)
            from Ticket ticket
            join ticket.requester requester
            left join ticket.assignedAgent assignedAgent
            left join ticket.asset asset
            where ticket.organization.id = :organizationId
              and (:requesterOnly = false or requester.id = :requesterId)
              and (:status is null or ticket.status = :status)
              and (:priority is null or ticket.priority = :priority)
              and (:search = '' or lower(ticket.title) like concat('%', :search, '%'))
            """,
            countQuery = """
            select count(ticket)
            from Ticket ticket
            join ticket.requester requester
            where ticket.organization.id = :organizationId
              and (:requesterOnly = false or requester.id = :requesterId)
              and (:status is null or ticket.status = :status)
              and (:priority is null or ticket.priority = :priority)
              and (:search = '' or lower(ticket.title) like concat('%', :search, '%'))
            """)
    Page<TicketSummary> searchVisibleTickets(@Param("organizationId") Long organizationId,
            @Param("requesterId") Long requesterId, @Param("requesterOnly") boolean requesterOnly,
            @Param("status") TicketStatus status, @Param("priority") TicketPriority priority,
            @Param("search") String search, Pageable pageable);

    @Query("""
            select new com.adria.atlasdesk.ticket.TicketSummary(
                ticket.id, ticket.organization.id, ticket.title, ticket.description,
                requester.id, requester.name, assignedAgent.id, assignedAgent.name,
                asset.id, asset.assetNumber, ticket.priority, ticket.status,
                ticket.createdAt, ticket.updatedAt, ticket.dueAt, ticket.resolvedAt, ticket.closedAt)
            from Ticket ticket
            join ticket.requester requester
            left join ticket.assignedAgent assignedAgent
            left join ticket.asset asset
            where ticket.id = :id and ticket.organization.id = :organizationId
              and (:requesterOnly = false or requester.id = :requesterId)
            """)
    Optional<TicketSummary> findVisibleSummary(@Param("id") Long id,
            @Param("organizationId") Long organizationId, @Param("requesterId") Long requesterId,
            @Param("requesterOnly") boolean requesterOnly);
}
