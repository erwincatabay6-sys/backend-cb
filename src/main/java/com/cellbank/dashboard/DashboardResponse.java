package com.cellbank.dashboard;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.cellbank.repair.RepairStatus;

public record DashboardResponse(
        boolean technicianOnly,
        Summary summary,
        Map<RepairStatus, Long> statusCounts,
        List<RepairItem> attentionRepairs,
        List<TechnicianWorkload> technicianWorkload,
        List<RepairItem> recentActiveRepairs,
        FinancialSnapshot financialSnapshot
) {

    public record Summary(
            long totalRepairs,
            long activeRepairs,
            long readyForRelease,
            long completedRepairs
    ) {
    }

    public record RepairItem(
            Long id,
            String reference,
            String customer,
            String device,
            String technician,
            RepairStatus status
    ) {
    }

    public record TechnicianWorkload(
            Long id,
            String name,
            long activeRepairs,
            long totalRepairs
    ) {
    }

    public record FinancialSnapshot(
            BigDecimal totalAgreedValue,
            BigDecimal totalCollected,
            BigDecimal outstandingBalance
    ) {
    }
}