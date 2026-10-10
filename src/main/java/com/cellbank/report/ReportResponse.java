package com.cellbank.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.cellbank.repair.RepairStatus;

public record ReportResponse(
        LocalDate startDate,
        LocalDate endDate,
        String timeZone,
        RepairSummary repairSummary,
        Map<RepairStatus, Long> statusCounts,
        List<RepairVolumePoint> repairVolume,
        FinancialSummary financialSummary,
        List<PaymentTrendPoint> paymentTrend,
        List<FinancialRecord> financialRecords,
        List<TechnicianActivity> technicianActivity,
        List<RepairRecord> repairRecords
) {

    public record RepairSummary(
            long totalRepairs,
            long activeRepairs,
            long completedRepairs,
            long cancelledRepairs
    ) {
    }

    public record RepairVolumePoint(
            String month,
            long value
    ) {
    }

    public record FinancialSummary(
            BigDecimal totalAgreedValue,
            BigDecimal totalPaymentsCollected,
            BigDecimal totalOutstandingBalance,
            long paidRepairs,
            long partiallyPaidRepairs,
            long unpaidRepairs,
            long priceNotAgreedRepairs
    ) {
    }

    public record PaymentTrendPoint(
            String month,
            BigDecimal value
    ) {
    }

    public record FinancialRecord(
            Long id,
            String reference,
            String customer,
            BigDecimal agreedPrice,
            BigDecimal totalPaid,
            BigDecimal balance,
            String paymentStatus
    ) {
    }

    public record TechnicianActivity(
            Long id,
            String name,
            long repairsHandled,
            long activeRepairs,
            long completedRepairs
    ) {
    }

    public record RepairRecord(
            Long id,
            String reference,
            LocalDate createdAt,
            String customer,
            String device,
            Long technicianId,
            String technician,
            RepairStatus status
    ) {
    }
}
