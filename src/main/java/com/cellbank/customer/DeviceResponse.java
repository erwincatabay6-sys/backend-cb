package com.cellbank.customer;

import java.time.Instant;

public record DeviceResponse(
        Long id,
        Long customerId,
        String type,
        String brand,
        String model,
        String serialNumber,
        String imei,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {

    public static DeviceResponse from(Device device) {
        return new DeviceResponse(
                device.getId(),
                device.getCustomerId(),
                device.getType(),
                device.getBrand(),
                device.getModel(),
                device.getSerialNumber(),
                device.getImei(),
                device.getNotes(),
                device.getCreatedAt(),
                device.getUpdatedAt()
        );
    }
}
