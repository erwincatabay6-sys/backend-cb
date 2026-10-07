package com.cellbank.repair;

import java.math.BigDecimal;
import java.time.Instant;

public record RepairPartResponse(
        Long id,
        Long repairJobId,
        String name,
        Integer quantity,
        BigDecimal unitCost,
        BigDecimal subtotal,
        Long recordedById,
        String recordedByName,
        Instant recordedAt
) {

    public static RepairPartResponse from(
            RepairPartUsage part,
            String recordedByName) {

        BigDecimal subtotal = part.getUnitCost()
                .multiply(BigDecimal.valueOf(part.getQuantity()));

        return new RepairPartResponse(
                part.getId(),
                part.getRepairJobId(),
                part.getPartName(),
                part.getQuantity(),
                part.getUnitCost(),
                subtotal,
                part.getRecordedById(),
                recordedByName,
                part.getRecordedAt()
        );
    }
}
