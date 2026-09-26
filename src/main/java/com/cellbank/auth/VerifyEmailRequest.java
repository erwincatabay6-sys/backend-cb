package com.cellbank.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyEmailRequest(

        @NotBlank(message = "Verification token is required.")
        @Pattern(
                regexp = "[A-Za-z0-9_-]{43}",
                message = "Invalid verification token format."
        )
        String token

) {
}
