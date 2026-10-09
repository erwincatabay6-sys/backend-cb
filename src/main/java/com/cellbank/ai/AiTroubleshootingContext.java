package com.cellbank.ai;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.cellbank.repair.RepairProblemCategory;
import com.cellbank.repair.RepairStatus;

public record AiTroubleshootingContext(
        Long repairJobId,
        String repairReference,
        DeviceDetails device,
        String reportedProblem,
        RepairProblemCategory problemCategory,
        String serviceType,
        RepairStatus status,
        Instant repairUpdatedAt,
        List<FindingDetails> findings,
        List<PartDetails> partsUsed,
        List<PreviousRepairDetails> previousRepairs,
        List<RepeatedProblemDetails> repeatedProblems) {

    public AiTroubleshootingContext {
        Objects.requireNonNull(repairJobId, "Repair ID is required.");
        Objects.requireNonNull(device, "Device details are required.");
        Objects.requireNonNull(status, "Repair status is required.");

        findings = immutableList(findings);
        partsUsed = immutableList(partsUsed);
        previousRepairs = immutableList(previousRepairs);
        repeatedProblems = immutableList(repeatedProblems);
    }

    public record DeviceDetails(
            String type,
            String brand,
            String model) {
    }

    public record FindingDetails(
            String description,
            String diagnosis,
            String actionTaken,
            Instant recordedAt) {
    }

    public record PartDetails(
            String partName,
            Integer quantity,
            Instant recordedAt) {
    }

    public record PreviousRepairDetails(
            Long repairJobId,
            String repairReference,
            String reportedProblem,
            RepairProblemCategory problemCategory,
            String serviceType,
            RepairStatus status,
            Instant createdAt,
            List<FindingDetails> findings,
            List<PartDetails> partsUsed) {

        public PreviousRepairDetails {
            findings = immutableList(findings);
            partsUsed = immutableList(partsUsed);
        }
    }

    /**
     * A category recorded in at least two non-cancelled repairs
     * for the same device.
     *
     * The count includes the current repair when eligible.
     * OTHER and uncategorized repairs are excluded.
     *
     * This indicates repeated category occurrences, not proof
     * that the repairs have the same underlying fault.
     */
    public record RepeatedProblemDetails(
            RepairProblemCategory problemCategory,
            int repairCount) {

        public RepeatedProblemDetails {
            if (problemCategory == null
                    || problemCategory == RepairProblemCategory.OTHER) {
                throw new IllegalArgumentException(
                        "A repeated problem requires a specific category.");
            }

            if (repairCount < 2) {
                throw new IllegalArgumentException(
                        "A repeated problem requires at least two repairs.");
            }
        }
    }

    private static <T> List<T> immutableList(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    @Override
    public String toString() {
        return "AiTroubleshootingContext[repairJobId="
                + repairJobId
                + ", findingsCount="
                + findings.size()
                + ", partsUsedCount="
                + partsUsed.size()
                + ", previousRepairsCount="
                + previousRepairs.size()
                + ", repeatedProblemsCount="
                + repeatedProblems.size()
                + "]";
    }
}