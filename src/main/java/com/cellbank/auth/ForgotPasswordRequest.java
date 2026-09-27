package com.cellbank.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ForgotPasswordRequest(

        @NotBlank(message = "Enter your username or registered email address.")
        @Size(max = 254, message = "Username or email is too long.")
        String identifier

) {
}
