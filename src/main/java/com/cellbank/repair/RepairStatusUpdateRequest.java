package com.cellbank.repair;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RepairStatusUpdateRequest(

        @NotNull(message = "Select a repair status.")
        RepairStatus status,

        @Size(
                max = 2000,
                message = "Internal note must not exceed 2000 characters."
        )
        String note,

        @Size(
                max = 2000,
                message = "Customer update must not exceed 2000 characters."
        )
        String customerUpdate,

        @NotNull(
                message = "The repair's last-updated time is required."
        )
        Instant expectedUpdatedAt

) {

    public RepairStatusUpdateRequest {
        if (note != null) {
            note = note.strip();

            if (note.isEmpty()) {
                note = null;
            }
        }

        if (customerUpdate != null) {
            customerUpdate = customerUpdate.strip();

            if (customerUpdate.isEmpty()) {
                customerUpdate = null;
            }
        }
    }
}