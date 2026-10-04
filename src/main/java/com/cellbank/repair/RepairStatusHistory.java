package com.cellbank.repair;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "repair_status_history", schema = "public")
public class RepairStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "repair_job_id", nullable = false, updatable = false)
    private Long repairJobId;

    @Column(name = "changed_by_id", nullable = false, updatable = false)
    private Long changedById;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 30, updatable = false)
    private RepairStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "new_status",
            nullable = false,
            length = 30,
            updatable = false
    )
    private RepairStatus newStatus;

    @Column(name = "note", columnDefinition = "text", updatable = false)
    private String note;

    @Column(
            name = "changed_at",
            nullable = false,
            updatable = false,
            columnDefinition = "timestamp with time zone"
    )
    private Instant changedAt;

    protected RepairStatusHistory() {
    }

    public RepairStatusHistory(
            Long repairJobId,
            Long changedById,
            RepairStatus previousStatus,
            RepairStatus newStatus,
            String note) {

        this.repairJobId = repairJobId;
        this.changedById = changedById;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.note = note;
    }

    @PrePersist
    protected void onCreate() {
        changedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getRepairJobId() {
        return repairJobId;
    }

    public Long getChangedById() {
        return changedById;
    }

    public RepairStatus getPreviousStatus() {
        return previousStatus;
    }

    public RepairStatus getNewStatus() {
        return newStatus;
    }

    public String getNote() {
        return note;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
