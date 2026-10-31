package com.adria.atlasdesk.ticket;

import java.time.Instant;
import com.adria.atlasdesk.asset.Asset;
import com.adria.atlasdesk.organization.Organization;
import com.adria.atlasdesk.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 4000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_agent_id")
    private User assignedAgent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Asset asset;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TicketStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Ticket() {
    }

    public Ticket(Organization organization, String title, String description, User requester, Asset asset,
            TicketPriority priority, Instant createdAt, Instant dueAt) {
        this.organization = organization;
        this.title = title;
        this.description = description;
        this.requester = requester;
        this.asset = asset;
        this.priority = priority;
        this.status = TicketStatus.OPEN;
        this.createdAt = createdAt;
        this.dueAt = dueAt;
    }

    @PrePersist
    void setCreationTimestamps() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    void setUpdatedTimestamp() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public User getRequester() {
        return requester;
    }

    public User getAssignedAgent() {
        return assignedAgent;
    }

    public Asset getAsset() {
        return asset;
    }

    public TicketPriority getPriority() {
        return priority;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public long getVersion() {
        return version;
    }

    public void assignTo(User agent) {
        if (status != TicketStatus.OPEN && status != TicketStatus.IN_PROGRESS) {
            throw new TicketConflictException("Only open or in-progress tickets can be assigned");
        }
        assignedAgent = agent;
    }

    public void transition(TicketCommand command, Instant occurredAt, boolean requireApproval) {
        switch (command) {
            case START -> {
                requireStatus(TicketStatus.OPEN, command);
                status = TicketStatus.IN_PROGRESS;
            }
            case RESOLVE -> {
                requireStatus(TicketStatus.IN_PROGRESS, command);
                if (requireApproval) {
                    status = TicketStatus.PENDING_APPROVAL;
                } else {
                    status = TicketStatus.RESOLVED;
                    resolvedAt = occurredAt;
                }
            }
            case APPROVE -> {
                requireStatus(TicketStatus.PENDING_APPROVAL, command);
                status = TicketStatus.RESOLVED;
                resolvedAt = occurredAt;
            }
            case REJECT -> {
                requireStatus(TicketStatus.PENDING_APPROVAL, command);
                status = TicketStatus.IN_PROGRESS;
                resolvedAt = null;
            }
            case CLOSE -> {
                requireStatus(TicketStatus.RESOLVED, command);
                status = TicketStatus.CLOSED;
                closedAt = occurredAt;
            }
        }
    }

    private void requireStatus(TicketStatus required, TicketCommand command) {
        if (status != required) {
            throw new TicketConflictException(
                    "Cannot " + command.name().toLowerCase() + " a ticket in " + status.name() + " status");
        }
    }
}
