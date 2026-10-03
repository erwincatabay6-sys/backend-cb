package com.cellbank.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DeviceRequest(

        @NotBlank(message = "Select a device type.")
        @Pattern(
                regexp = "Mobile Phone|Laptop|Desktop Computer|Tablet|Other",
                message = "Select a supported device type."
        )
        String type,

        @NotBlank(message = "Brand is required.")
        @Size(max = 80, message = "Brand must not exceed 80 characters.")
        String brand,

        @NotBlank(message = "Model is required.")
        @Size(max = 120, message = "Model must not exceed 120 characters.")
        String model,

        @Size(
                max = 120,
                message = "Serial number must not exceed 120 characters."
        )
        String serialNumber,

        @Size(max = 20, message = "IMEI must not exceed 20 characters.")
        String imei,

        @Size(max = 2000, message = "Notes must not exceed 2000 characters.")
        String notes

) {

    public DeviceRequest {
        type = normalize(type);
        brand = normalize(brand);
        model = normalize(model);
        serialNumber = normalize(serialNumber);
        imei = normalize(imei);
        notes = normalize(notes);
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
        return "DeviceRequest[redacted]";
    }
}