package com.cellbank.repair;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public record RepairEstimateUpdateRequest(

        @NotNull(message = "Enter the estimated repair cost.")
        @DecimalMin(
                value = "0.00",
                message = "Estimated cost must not be negative."
        )
        @Digits(
                integer = 10,
                fraction = 2,
                message = "Estimated cost must have at most 10 whole-number digits and 2 decimal places."
        )
        BigDecimal estimatedCost,

        @NotNull(
                message = "The repair's last-updated time is required."
        )
        Instant expectedUpdatedAt

) {
}