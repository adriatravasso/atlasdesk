package com.adria.atlasdesk.ticket;

import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketHistoryRepository extends JpaRepository<TicketHistoryEvent, Long> {

    @Modifying(flushAutomatically = true)
    @Query("delete from TicketHistoryEvent event where event.organizationId = :organizationId")
    int deleteAllByOrganizationId(@Param("organizationId") Long organizationId);

    @Query("""
            select new com.adria.atlasdesk.ticket.TicketHistoryEntry(
                event.id, event.eventType, event.actorId, event.actorName, event.description, event.occurredAt)
            from TicketHistoryEvent event
            where event.organizationId = :organizationId and event.ticketId = :ticketId
            order by event.occurredAt asc, event.id asc
            """)
    List<TicketHistoryEntry> findTicketHistory(@Param("organizationId") Long organizationId,
            @Param("ticketId") Long ticketId);
}
