package com.adria.atlasdesk.ticket;

import java.time.Instant;
import java.util.Locale;
import com.adria.atlasdesk.asset.Asset;
import com.adria.atlasdesk.asset.AssetRepository;
import com.adria.atlasdesk.organization.Organization;
import com.adria.atlasdesk.organization.OrganizationRepository;
import com.adria.atlasdesk.security.AuthenticatedUser;
import com.adria.atlasdesk.security.CurrentUserContext;
import com.adria.atlasdesk.user.User;
import com.adria.atlasdesk.user.UserRepository;
import com.adria.atlasdesk.user.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TicketDirectoryService {

    private final TicketRepository tickets;
    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final AssetRepository assets;
    private final CurrentUserContext currentUser;

    public TicketDirectoryService(TicketRepository tickets, OrganizationRepository organizations,
            UserRepository users, AssetRepository assets, CurrentUserContext currentUser) {
        this.tickets = tickets;
        this.organizations = organizations;
        this.users = users;
        this.assets = assets;
        this.currentUser = currentUser;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT', 'REQUESTER')")
    public TicketSummary create(String title, String description, TicketPriority priority, Long assetId) {
        AuthenticatedUser actor = currentUser.requireUser();
        Organization organization = organizations.findById(actor.organizationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Current organization was not found"));
        User requester = users.findByIdAndOrganizationId(actor.id(), actor.organizationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Authenticated user is unavailable"));
        Asset asset = assetId == null ? null : assets.findByIdAndOrganizationId(assetId, actor.organizationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Asset not found in the current organization"));

        Instant createdAt = Instant.now();
        Instant dueAt = createdAt.plusSeconds((long) slaHours(organization, priority) * 60 * 60);
        Ticket ticket = new Ticket(organization, normalizeRequired(title, "Title"),
                normalizeRequired(description, "Description"), requester, asset, priority, createdAt, dueAt);
        Ticket saved = tickets.saveAndFlush(ticket);
        return tickets.findVisibleSummary(saved.getId(), actor.organizationId(), actor.id(), false)
                .orElseThrow(TicketNotFoundException::new);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT', 'REQUESTER')")
    public Page<TicketSummary> list(TicketStatus status, TicketPriority priority, String search, Pageable pageable) {
        AuthenticatedUser actor = currentUser.requireUser();
        boolean requesterOnly = actor.role() == UserRole.REQUESTER;
        return tickets.searchVisibleTickets(actor.organizationId(), actor.id(), requesterOnly,
                status, priority, normalizeSearch(search), pageable);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT', 'REQUESTER')")
    public TicketSummary get(Long id) {
        AuthenticatedUser actor = currentUser.requireUser();
        boolean requesterOnly = actor.role() == UserRole.REQUESTER;
        return tickets.findVisibleSummary(id, actor.organizationId(), actor.id(), requesterOnly)
                .orElseThrow(TicketNotFoundException::new);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public TicketSummary assign(Long ticketId, Long assignedAgentId) {
        AuthenticatedUser actor = currentUser.requireUser();
        Ticket ticket = findTicket(ticketId, actor);
        User agent = users.findByIdAndOrganizationId(assignedAgentId, actor.organizationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Assignee not found in the current organization"));
        if (!agent.isActive() || agent.getRole() != UserRole.AGENT) {
            throw new TicketConflictException("Assignee must be an active agent in the current organization");
        }
        ticket.assignTo(agent);
        return saveAndRead(ticket, actor);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT', 'REQUESTER')")
    public TicketSummary transition(Long ticketId, TicketCommand command) {
        AuthenticatedUser actor = currentUser.requireUser();
        Ticket ticket = findTicket(ticketId, actor);
        authorizeTransition(ticket, actor, command);
        ticket.transition(command, Instant.now());
        return saveAndRead(ticket, actor);
    }

    private Ticket findTicket(Long ticketId, AuthenticatedUser actor) {
        return tickets.findByIdAndOrganizationId(ticketId, actor.organizationId())
                .orElseThrow(TicketNotFoundException::new);
    }

    private TicketSummary saveAndRead(Ticket ticket, AuthenticatedUser actor) {
        Ticket saved = tickets.saveAndFlush(ticket);
        boolean requesterOnly = actor.role() == UserRole.REQUESTER;
        return tickets.findVisibleSummary(saved.getId(), actor.organizationId(), actor.id(), requesterOnly)
                .orElseThrow(TicketNotFoundException::new);
    }

    private static void authorizeTransition(Ticket ticket, AuthenticatedUser actor, TicketCommand command) {
        if (command == TicketCommand.CLOSE) {
            boolean requesterOwnTicket = actor.role() == UserRole.REQUESTER
                    && ticket.getRequester().getId().equals(actor.id());
            if (actor.role() != UserRole.ADMIN && !requesterOwnTicket) {
                throw new AccessDeniedException(
                        "Only the requester or an administrator can close this ticket");
            }
            return;
        }

        boolean administrator = actor.role() == UserRole.ADMIN;
        boolean assignedAgent = actor.role() == UserRole.AGENT
                && ticket.getAssignedAgent() != null
                && ticket.getAssignedAgent().getId().equals(actor.id());
        if (!administrator && !assignedAgent) {
            throw new AccessDeniedException(
                    "Only the assigned agent or an administrator can handle this ticket");
        }
    }

    private static int slaHours(Organization organization, TicketPriority priority) {
        return switch (priority) {
            case LOW -> organization.getSlaLowHours();
            case MEDIUM -> organization.getSlaMediumHours();
            case HIGH -> organization.getSlaHighHours();
        };
    }

    private static String normalizeRequired(String value, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must not be blank");
        }
        return normalized;
    }

    private static String normalizeSearch(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
