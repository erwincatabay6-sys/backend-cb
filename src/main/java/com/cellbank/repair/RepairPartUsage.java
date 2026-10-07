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
@Table(name = "repair_part_usage", schema = "public")
public class RepairPartUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "repair_job_id", nullable = false, updatable = false)
    private Long repairJobId;

    @Column(name = "recorded_by_id", nullable = false, updatable = false)
    private Long recordedById;

    @Column(
            name = "part_name",
            nullable = false,
            length = 150,
            updatable = false
    )
    private String partName;

    @Column(name = "quantity", nullable = false, updatable = false)
    private Integer quantity;

    @Column(
            name = "unit_cost",
            nullable = false,
            precision = 12,
            scale = 2,
            updatable = false
    )
    private BigDecimal unitCost;

    @Column(
            name = "recorded_at",
            nullable = false,
            updatable = false,
            columnDefinition = "timestamp with time zone"
    )
    private Instant recordedAt;

    protected RepairPartUsage() {
    }

    public RepairPartUsage(
            Long repairJobId,
            Long recordedById,
            String partName,
            Integer quantity,
            BigDecimal unitCost) {

        this.repairJobId = repairJobId;
        this.recordedById = recordedById;
        this.partName = partName;
        this.quantity = quantity;
        this.unitCost = unitCost;
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

    public String getPartName() {
        return partName;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
