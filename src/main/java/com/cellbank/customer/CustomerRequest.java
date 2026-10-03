package com.cellbank.customer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerRequest(

        @NotBlank(message = "Customer name is required.")
        @Size(
                max = 120,
                message = "Name must not exceed 120 characters."
        )
        String name,

        @NotBlank(message = "Phone number is required.")
        @Pattern(
                regexp = "\\+?[0-9]{7,15}",
                message = "Enter 7 to 15 digits, optionally starting with +."
        )
        String phone,

        @Email(message = "Enter a valid email address.")
        @Size(
                max = 254,
                message = "Email must not exceed 254 characters."
        )
        String email,

        @Size(
                max = 255,
                message = "Address must not exceed 255 characters."
        )
        String address

) {

    public CustomerRequest {
        name = normalize(name);
        phone = normalizePhone(phone);
        email = normalize(email);
        address = normalize(address);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizePhone(String value) {
        String normalized = normalize(value);

        if (normalized == null) {
            return null;
        }

        return normalize(
                normalized.replaceAll("[ ()-]", "")
        );
    }

    @Override
    public String toString() {
        return "CustomerRequest[redacted]";
    }
}