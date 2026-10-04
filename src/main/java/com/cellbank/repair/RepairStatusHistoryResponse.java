package com.cellbank.repair;

import java.time.Instant;

public record RepairStatusHistoryResponse(
        Long id,
        Long repairJobId,
        Long changedById,
        String changedByName,
        RepairStatus previousStatus,
        RepairStatus newStatus,
        String note,
        Instant changedAt
) {

    public static RepairStatusHistoryResponse from(
            RepairStatusHistory history,
            String changedByName) {

        return new RepairStatusHistoryResponse(
                history.getId(),
                history.getRepairJobId(),
                history.getChangedById(),
                changedByName,
                history.getPreviousStatus(),
                history.getNewStatus(),
                history.getNote(),
                history.getChangedAt()
        );
    }

    public static RepairStatusHistoryResponse from(
            RepairStatusHistory history) {

        return from(history, null);
    }
}