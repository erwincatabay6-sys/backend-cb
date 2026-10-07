package com.cellbank.repair;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RepairCreateRequest(

        @NotNull(message = "Select a customer.")
        @Positive(message = "Select a valid customer.")
        Long customerId,

        @NotNull(message = "Select a device.")
        @Positive(message = "Select a valid device.")
        Long deviceId,

        @NotBlank(message = "Reported problem is required.")
        @Size(
                max = 5000,
                message = "Reported problem must not exceed 5000 characters."
        )
        String reportedProblem,

        @NotNull(message = "Select a problem category.")
        RepairProblemCategory problemCategory,

        @NotBlank(message = "Select a service type.")
        @Pattern(
                regexp = "DIAGNOSTIC|HARDWARE_REPAIR|SOFTWARE_REPAIR|MAINTENANCE|OTHER",
                message = "Select a supported service type."
        )
        String serviceType,

        @Size(
                max = 2000,
                message = "Accessories received must not exceed 2000 characters."
        )
        String accessoriesReceived,

        @Size(
                max = 5000,
                message = "Intake notes must not exceed 5000 characters."
        )
        String intakeNotes,

        @Positive(message = "Select a valid technician.")
        Long assignedTechnicianId,

        @DecimalMin(
                value = "0.00",
                message = "Estimated cost must not be negative."
        )
        @Digits(
                integer = 10,
                fraction = 2,
                message = "Estimated cost allows up to 10 whole digits and 2 decimal places."
        )
        BigDecimal estimatedCost

) {

    public RepairCreateRequest {
        reportedProblem = normalize(reportedProblem);
        serviceType = normalize(serviceType);
        accessoriesReceived = normalize(accessoriesReceived);
        intakeNotes = normalize(intakeNotes);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Override
    public String toString() {
        return "RepairCreateRequest[redacted]";
    }
}