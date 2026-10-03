package com.cellbank.customer;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers/{customerId}/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping
    public List<DeviceResponse> getDevices(
            @PathVariable("customerId") Long customerId) {

        return deviceService.getDevices(customerId);
    }

    @GetMapping("/{deviceId}")
    public DeviceResponse getDevice(
            @PathVariable("customerId") Long customerId,
            @PathVariable("deviceId") Long deviceId) {

        return deviceService.getDevice(customerId, deviceId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeviceResponse createDevice(
            @PathVariable("customerId") Long customerId,
            @Valid @RequestBody DeviceRequest request) {

        return deviceService.createDevice(customerId, request);
    }

    @PutMapping("/{deviceId}")
    public DeviceResponse updateDevice(
            @PathVariable("customerId") Long customerId,
            @PathVariable("deviceId") Long deviceId,
            @Valid @RequestBody DeviceRequest request) {

        return deviceService.updateDevice(
                customerId,
                deviceId,
                request
        );
    }
}