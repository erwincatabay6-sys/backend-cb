package com.cellbank.auth;


import jakarta.validation.constraints.NotNull;

public record UpdateStaffAccessRequest(
        @NotNull(message = "Active status is required.")
        Boolean active
) {
}