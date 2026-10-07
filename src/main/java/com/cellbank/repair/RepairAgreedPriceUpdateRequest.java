package com.cellbank.repair;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public record RepairAgreedPriceUpdateRequest(

        @NotNull(message = "Enter the agreed repair price.")
        @DecimalMin(
                value = "0.00",
                message = "Agreed price must not be negative."
        )
        @Digits(
                integer = 10,
                fraction = 2,
                message = "Agreed price must have at most 10 whole-number digits and 2 decimal places."
        )
        BigDecimal agreedPrice,

        @NotNull(
                message = "The repair's last-updated time is required."
        )
        Instant expectedUpdatedAt

) {
}