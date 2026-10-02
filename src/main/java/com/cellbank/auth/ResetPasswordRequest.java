package com.cellbank.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(

        @NotBlank(message = "A reset token is required.")
        @Pattern(
                regexp = "[A-Za-z0-9_-]{43}",
                message = "Invalid reset token."
        )
        String token,

        @NotBlank(message = "Enter a new password.")
        @Size(
                min = 8,
                max = 72,
                message = "Password must contain between 8 and 72 characters."
        )
        String newPassword

) {
    @Override
    public String toString() {
        return "ResetPasswordRequest[redacted]";
    }
}
