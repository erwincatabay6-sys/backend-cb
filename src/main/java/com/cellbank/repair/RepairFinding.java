package com.cellbank.repair;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "repair_findings", schema = "public")
public class RepairFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "repair_job_id", nullable = false, updatable = false)
    private Long repairJobId;

    @Column(name = "recorded_by_id", nullable = false, updatable = false)
    private Long recordedById;

    @Column(
            name = "description",
            nullable = false,
            columnDefinition = "text",
            updatable = false
    )
    private String description;

    @Column(
            name = "diagnosis",
            columnDefinition = "text",
            updatable = false
    )
    private String diagnosis;

    @Column(
            name = "action_taken",
            columnDefinition = "text",
            updatable = false
    )
    private String actionTaken;

    @Column(
            name = "recorded_at",
            nullable = false,
            updatable = false,
            columnDefinition = "timestamp with time zone"
    )
    private Instant recordedAt;

    protected RepairFinding() {
    }

    public RepairFinding(
            Long repairJobId,
            Long recordedById,
            String description,
            String diagnosis,
            String actionTaken) {

        this.repairJobId = repairJobId;
        this.recordedById = recordedById;
        this.description = description;
        this.diagnosis = diagnosis;
        this.actionTaken = actionTaken;
    }

    @PrePersist
    protected void onCreate() {
        recordedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getRepairJobId() {
        return repairJobId;
    }

    public Long getRecordedById() {
        return recordedById;
    }

    public String getDescription() {
        return description;
    }

    public String getDiagnosis() {
        return diagnosis;
    }

    public String getActionTaken() {
        return actionTaken;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}