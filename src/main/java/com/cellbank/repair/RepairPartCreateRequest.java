package com.cellbank.repair;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RepairPartCreateRequest(

        @NotBlank(message = "Enter a part name.")
        @Size(
                max = 150,
                message = "Part name must not exceed 150 characters."
        )
        String partName,

        @NotNull(message = "Enter a quantity.")
        @Min(value = 1, message = "Quantity must be at least 1.")
        Integer quantity,

        @NotNull(message = "Enter the unit cost.")
        @DecimalMin(
                value = "0.00",
                message = "Unit cost must not be negative."
        )
        @Digits(
                integer = 10,
                fraction = 2,
                message = "Unit cost must have at most 10 whole-number digits and 2 decimal places."
        )
        BigDecimal unitCost,

        @NotNull(
                message = "The repair's last-updated time is required."
        )
        Instant expectedUpdatedAt

) {

    public RepairPartCreateRequest {
        if (partName != null) {
            partName = partName.strip();

            if (partName.isEmpty()) {
                partName = null;
            }
        }
    }
}
