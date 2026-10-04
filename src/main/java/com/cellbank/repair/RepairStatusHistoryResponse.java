package com.cellbank.repair;

import java.time.Instant;

public record RepairStatusHistoryResponse(
        Long id,
        Long repairJobId,
        Long changedById,
        RepairStatus previousStatus,
        RepairStatus newStatus,
        String note,
        Instant changedAt
) {

    public static RepairStatusHistoryResponse from(
            RepairStatusHistory history) {

        return new RepairStatusHistoryResponse(
                history.getId(),
                history.getRepairJobId(),
                history.getChangedById(),
                history.getPreviousStatus(),
                history.getNewStatus(),
                history.getNote(),
                history.getChangedAt()
        );
    }
}