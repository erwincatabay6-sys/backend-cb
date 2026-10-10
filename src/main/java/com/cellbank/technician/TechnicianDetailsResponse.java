package com.cellbank.technician;

import java.util.List;
import java.util.Map;

import com.cellbank.repair.RepairStatus;

public record TechnicianDetailsResponse(
        TechnicianResponse technician,
        Map<RepairStatus, Long> statusCounts,
        List<AssignedRepair> activeRepairs,
        List<AssignedRepair> repairHistory
) {

    public record AssignedRepair(
            Long id,
            String reference,
            String customer,
            String device,
            RepairStatus status
    ) {
    }
}
