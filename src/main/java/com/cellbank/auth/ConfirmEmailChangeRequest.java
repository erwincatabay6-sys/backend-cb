package com.cellbank.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConfirmEmailChangeRequest(

        @NotBlank(message = "Enter the confirmation code.")
        @Pattern(
                regexp = "[0-9]{6}",
                message = "The confirmation code must contain exactly 6 digits."
        )
        String code

) {
}