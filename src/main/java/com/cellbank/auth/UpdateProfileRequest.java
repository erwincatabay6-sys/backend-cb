package com.cellbank.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(

        @NotBlank(message = "Full name is required.")
        @Size(
                max = 120,
                message = "Full name must not exceed 120 characters."
        )
        String name

) {
}
