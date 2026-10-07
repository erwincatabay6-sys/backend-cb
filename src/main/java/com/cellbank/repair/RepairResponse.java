package com.cellbank.repair;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record RepairResponse(
        Long id,
        String repairReference,
        String trackingCode,
        Long customerId,
        Long deviceId,
        Long assignedTechnicianId,
        String assignedTechnicianName,
        Long createdById,
        String createdByName,
        String reportedProblem,
        RepairProblemCategory problemCategory,
        String serviceType,
        String accessoriesReceived,
        String intakeNotes,
        RepairStatus status,
        RepairPriority priority,
        BigDecimal estimatedCost,
        BigDecimal agreedPrice,
        LocalDate dueDate,
        Instant createdAt,
        Instant updatedAt
) {

    public static RepairResponse from(
            RepairJob repair,
            Long customerId,
            String assignedTechnicianName,
            String createdByName) {

        return new RepairResponse(
                repair.getId(),
                repair.getRepairReference(),
                repair.getTrackingCode(),
                customerId,
                repair.getDeviceId(),
                repair.getAssignedTechnicianId(),
                assignedTechnicianName,
                repair.getCreatedById(),
                createdByName,
                repair.getReportedProblem(),
                repair.getProblemCategory(),
                repair.getServiceType(),
                repair.getAccessoriesReceived(),
                repair.getIntakeNotes(),
                repair.getStatus(),
                repair.getPriority(),
                repair.getEstimatedCost(),
                repair.getAgreedPrice(),
                repair.getDueDate(),
                repair.getCreatedAt(),
                repair.getUpdatedAt()
        );
    }

    public static RepairResponse from(
            RepairJob repair,
            Long customerId) {

        return from(repair, customerId, null, null);
    }
}