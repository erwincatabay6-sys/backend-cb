package com.cellbank.customer;

import java.time.Instant;
import java.util.List;

public record CustomerResponse(
        Long id,
        String name,
        String phone,
        String email,
        String address,
        Instant createdAt,
        Instant updatedAt,
        List<DeviceResponse> devices
) {

    public static CustomerResponse from(
            Customer customer,
            List<DeviceResponse> devices) {

        return new CustomerResponse(
                customer.getId(),
                customer.getName(),
                customer.getPhone(),
                customer.getEmail(),
                customer.getAddress(),
                customer.getCreatedAt(),
                customer.getUpdatedAt(),
                List.copyOf(devices)
        );
    }
}