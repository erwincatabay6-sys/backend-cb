package com.cellbank.repair;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

public record RepairProblemCategoryUpdateRequest(

        @NotNull(message = "Select a problem category.")
        RepairProblemCategory problemCategory,

        @NotNull(
                message = "Reload the repair before updating its problem category."
        )
        Instant expectedUpdatedAt

) {
}
