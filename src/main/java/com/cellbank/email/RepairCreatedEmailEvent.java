package com.cellbank.email;

public record RepairCreatedEmailEvent(
        Long repairId,
        String recipient,
        String customerName,
        String repairReference,
        String deviceDescription,
        String trackingCode) {
}