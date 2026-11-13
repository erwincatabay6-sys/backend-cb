package com.cellbank.email;

public record RepairReadyEmailEvent(
        Long repairId,
        String recipient,
        String customerName,
        String repairReference,
        String deviceDescription,
        String trackingCode,
        String customerUpdate) {
}
