package com.cellbank.repair;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RepairAssignmentRequest(

        @Positive(message = "Select a valid technician.")
        Long assignedTechnicianId,

        @NotNull(message = "The repair's last-updated time is required.")
        Instant expectedUpdatedAt

) {
}