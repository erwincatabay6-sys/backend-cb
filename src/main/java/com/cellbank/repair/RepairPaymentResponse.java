package com.cellbank.repair;

import java.math.BigDecimal;
import java.time.Instant;

public record RepairPaymentResponse(
        Long id,
        Long repairJobId,
        BigDecimal amount,
        String note,
        Long recordedById,
        String recordedByName,
        Instant paidAt
) {

    public static RepairPaymentResponse from(
            RepairPayment payment,
            String recordedByName) {

        return new RepairPaymentResponse(
                payment.getId(),
                payment.getRepairJobId(),
                payment.getAmount(),
                payment.getNote(),
                payment.getRecordedById(),
                recordedByName,
                payment.getPaidAt()
        );
    }
}
