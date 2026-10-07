package com.cellbank.repair;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RepairPaymentCreateRequest(

        @NotNull(message = "Enter a payment amount.")
        @DecimalMin(
                value = "0.01",
                message = "Payment amount must be at least 0.01."
        )
        @Digits(
                integer = 10,
                fraction = 2,
                message = "Use up to 10 whole-number digits and 2 decimal places."
        )
        BigDecimal amount,

        @Size(
                max = 2000,
                message = "Payment note must not exceed 2000 characters."
        )
        String note,

        @NotNull(message = "Reload the repair before recording a payment.")
        Instant expectedUpdatedAt

) {

    public RepairPaymentCreateRequest {
        if (note != null) {
            note = note.strip();

            if (note.isEmpty()) {
                note = null;
            }
        }
    }
}
