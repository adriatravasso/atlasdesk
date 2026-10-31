package com.adria.atlasdesk.organization;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "organizations")
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "require_approval", nullable = false)
    private boolean requireApproval;

    @Column(name = "sla_low_hours", nullable = false)
    private int slaLowHours;

    @Column(name = "sla_medium_hours", nullable = false)
    private int slaMediumHours;

    @Column(name = "sla_high_hours", nullable = false)
    private int slaHighHours;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Organization() {
    }

    public Organization(String name, boolean requireApproval, int slaLowHours, int slaMediumHours, int slaHighHours) {
        this.name = name;
        this.requireApproval = requireApproval;
        this.slaLowHours = slaLowHours;
        this.slaMediumHours = slaMediumHours;
        this.slaHighHours = slaHighHours;
    }

    @PrePersist
    void setCreationTimestamps() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void setUpdatedTimestamp() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isRequireApproval() {
        return requireApproval;
    }

    public int getSlaLowHours() {
        return slaLowHours;
    }

    public int getSlaMediumHours() {
        return slaMediumHours;
    }

    public int getSlaHighHours() {
        return slaHighHours;
    }

    public void updateSlaHours(int lowHours, int mediumHours, int highHours) {
        slaLowHours = lowHours;
        slaMediumHours = mediumHours;
        slaHighHours = highHours;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
