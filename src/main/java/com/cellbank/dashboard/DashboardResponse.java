package com.cellbank.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
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
        FinancialSnapshot financialSnapshot,
        List<FrontDeskActivity> frontDeskActivity,
        LocalDate activityDate
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
            long totalRepairs,
            String profileImageUrl
    ) {
    }

    public record FrontDeskActivity(
            Long id,
            String name,
            long repairsRegistered,
            long paymentsRecorded,
            long repairsReleased,
            String profileImageUrl
    ) {
    }

    public record FinancialSnapshot(
            BigDecimal totalAgreedValue,
            BigDecimal totalCollected,
            BigDecimal outstandingBalance
    ) {
    }
}