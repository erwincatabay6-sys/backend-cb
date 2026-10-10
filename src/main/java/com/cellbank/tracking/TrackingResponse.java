package com.cellbank.tracking;

import java.time.Instant;
import java.util.List;

import com.cellbank.repair.RepairStatus;

public record TrackingResponse(
        String trackingCode,
        String device,
        RepairStatus status,
        Instant receivedAt,
        Instant lastUpdated,
        List<StatusEntry> statusHistory
) {

    public record StatusEntry(
            RepairStatus status,
            Instant changedAt,
            String description
    ) {
    }
}