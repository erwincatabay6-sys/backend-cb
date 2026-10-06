package com.cellbank.repair;

import java.time.Instant;

public record RepairFindingResponse(
        Long id,
        Long repairJobId,
        String finding,
        String diagnosis,
        String actionTaken,
        Long recordedById,
        String recordedByName,
        Instant recordedAt
) {

    public static RepairFindingResponse from(
            RepairFinding finding,
            String recordedByName) {

        return new RepairFindingResponse(
                finding.getId(),
                finding.getRepairJobId(),
                finding.getDescription(),
                finding.getDiagnosis(),
                finding.getActionTaken(),
                finding.getRecordedById(),
                recordedByName,
                finding.getRecordedAt()
        );
    }
}