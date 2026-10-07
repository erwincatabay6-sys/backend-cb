package com.cellbank.repair;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "payments", schema = "public")
public class RepairPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "repair_job_id", nullable = false, updatable = false)
    private Long repairJobId;

    @Column(name = "recorded_by_id", nullable = false, updatable = false)
    private Long recordedById;

    @Column(
            name = "amount",
            nullable = false,
            precision = 12,
            scale = 2,
            updatable = false
    )
    private BigDecimal amount;

    @Column(name = "note", columnDefinition = "text", updatable = false)
    private String note;

    @Column(
            name = "paid_at",
            nullable = false,
            updatable = false,
            columnDefinition = "timestamp with time zone"
    )
    private Instant paidAt;

    protected RepairPayment() {
    }

    public RepairPayment(
            Long repairJobId,
            Long recordedById,
            BigDecimal amount,
            String note) {

        this.repairJobId = repairJobId;
        this.recordedById = recordedById;
        this.amount = amount;
        this.note = note;
    }

    @PrePersist
    protected void onCreate() {
        paidAt = Instant.now();
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

    public BigDecimal getAmount() {
        return amount;
    }

    public String getNote() {
        return note;
    }

    public Instant getPaidAt() {
        return paidAt;
    }
}
