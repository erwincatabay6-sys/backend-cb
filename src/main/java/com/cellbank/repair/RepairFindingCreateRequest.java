package com.cellbank.repair;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RepairFindingCreateRequest(

        @NotBlank(message = "Enter a finding.")
        @Size(max = 5000, message = "Finding must not exceed 5000 characters.")
        String finding,

        @Size(max = 5000, message = "Diagnosis must not exceed 5000 characters.")
        String diagnosis,

        @Size(max = 5000, message = "Action taken must not exceed 5000 characters.")
        String actionTaken,

        @NotNull(message = "The repair's last-updated time is required.")
        Instant expectedUpdatedAt

) {

    public RepairFindingCreateRequest {
        finding = normalize(finding);
        diagnosis = normalize(diagnosis);
        actionTaken = normalize(actionTaken);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
