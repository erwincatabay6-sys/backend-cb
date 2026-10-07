package com.cellbank.repair;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "repair_jobs", schema = "public")
public class RepairJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "repair_reference", nullable = false, length = 30)
    private String repairReference;

    @Column(name = "tracking_code", nullable = false, length = 50)
    private String trackingCode;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(name = "assigned_technician_id")
    private Long assignedTechnicianId;

    @Column(name = "created_by_id", nullable = false, updatable = false)
    private Long createdById;

    @Column(name = "reported_problem", nullable = false, columnDefinition = "text")
    private String reportedProblem;

    @Column(name = "service_type", nullable = false, length = 80)
    private String serviceType;

    @Column(name = "accessories_received", columnDefinition = "text")
    private String accessoriesReceived;

    @Column(name = "intake_notes", columnDefinition = "text")
    private String intakeNotes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private RepairStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private RepairPriority priority;

    @Column(name = "estimated_cost", precision = 12, scale = 2)
    private BigDecimal estimatedCost;

    @Column(name = "agreed_price", precision = 12, scale = 2)
    private BigDecimal agreedPrice;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false,
            columnDefinition = "timestamp with time zone"
    )
    private Instant createdAt;

    @Column(
            name = "updated_at",
            nullable = false,
            columnDefinition = "timestamp with time zone"
    )
    private Instant updatedAt;

    protected RepairJob() {
    }

    public RepairJob(
            String repairReference,
            String trackingCode,
            Long deviceId,
            Long assignedTechnicianId,
            Long createdById,
            String reportedProblem,
            String serviceType,
            String accessoriesReceived,
            String intakeNotes,
            RepairPriority priority,
            BigDecimal estimatedCost,
            BigDecimal agreedPrice,
            LocalDate dueDate) {

        this.repairReference = repairReference;
        this.trackingCode = trackingCode;
        this.deviceId = deviceId;
        this.assignedTechnicianId = assignedTechnicianId;
        this.createdById = createdById;
        this.reportedProblem = reportedProblem;
        this.serviceType = serviceType;
        this.accessoriesReceived = accessoriesReceived;
        this.intakeNotes = intakeNotes;
        this.status = RepairStatus.RECEIVED;
        this.priority = priority == null ? RepairPriority.NORMAL : priority;
        this.estimatedCost = estimatedCost;
        this.agreedPrice = agreedPrice;
        this.dueDate = dueDate;
    }
    
    void assignReferenceFromId() {
        if (id == null) {
            throw new IllegalStateException(
                    "The repair must be saved before assigning its reference."
            );
        }

        this.repairReference = String.format(
                java.util.Locale.ROOT,
                "CB-%06d",
                id
        );
    }
    
    void assignTechnician(Long technicianId) {
        this.assignedTechnicianId = technicianId;
    }
    
    void changeStatus(RepairStatus newStatus) {
        if (newStatus == null) {
            throw new IllegalArgumentException("Repair status is required.");
        }

        this.status = newStatus;
    }
    
    void updateEstimatedCost(BigDecimal estimatedCost) {
        if (estimatedCost == null || estimatedCost.signum() < 0) {
            throw new IllegalArgumentException(
                    "Estimated cost is required and must not be negative."
            );
        }

        this.estimatedCost = estimatedCost;
    }

    void updateAgreedPrice(BigDecimal agreedPrice) {
        if (agreedPrice == null || agreedPrice.signum() < 0) {
            throw new IllegalArgumentException(
                    "Agreed price is required and must not be negative."
            );
        }

        this.agreedPrice = agreedPrice;
    }
    
    void markPaymentRecorded() {
        this.updatedAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getRepairReference() {
        return repairReference;
    }

    public String getTrackingCode() {
        return trackingCode;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public Long getAssignedTechnicianId() {
        return assignedTechnicianId;
    }

    public Long getCreatedById() {
        return createdById;
    }

    public String getReportedProblem() {
        return reportedProblem;
    }

    public String getServiceType() {
        return serviceType;
    }

    public String getAccessoriesReceived() {
        return accessoriesReceived;
    }

    public String getIntakeNotes() {
        return intakeNotes;
    }

    public RepairStatus getStatus() {
        return status;
    }

    public RepairPriority getPriority() {
        return priority;
    }

    public BigDecimal getEstimatedCost() {
        return estimatedCost;
    }

    public BigDecimal getAgreedPrice() {
        return agreedPrice;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}