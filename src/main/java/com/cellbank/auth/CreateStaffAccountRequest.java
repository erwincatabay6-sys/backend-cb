package com.cellbank.auth;

import java.util.Set;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateStaffAccountRequest(

        @NotBlank(message = "Full name is required.")
        @Size(max = 120,
                message = "Full name must not exceed 120 characters.")
        String name,

        @NotBlank(message = "Username is required.")
        @Pattern(
                regexp = "[A-Za-z0-9._-]{3,50}",
                message = "Username must contain 3 to 50 letters, "
                        + "digits, dots, underscores or hyphens."
        )
        String username,

        @NotBlank(message = "Email is required.")
        @Email(message = "Email format is invalid.")
        @Size(max = 254,
                message = "Email must not exceed 254 characters.")
        String email,

        @NotEmpty(message = "At least one role is required.")
        Set<@NotBlank(message = "Role must not be blank.") String> roles,

        @NotBlank(message = "Initial password is required.")
        @Size(
                min = 8,
                max = 72,
                message = "Initial password must contain 8 to 72 characters."
        )
        String initialPassword

) {
    @Override
    public String toString() {
        return "CreateStaffAccountRequest[redacted]";
    }
}