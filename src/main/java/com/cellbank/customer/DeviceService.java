package com.cellbank.customer;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final CustomerRepository customerRepository;

    public DeviceService(
            DeviceRepository deviceRepository,
            CustomerRepository customerRepository) {

        this.deviceRepository = deviceRepository;
        this.customerRepository = customerRepository;
    }

    public List<DeviceResponse> getDevices(Long customerId) {
        requireCustomer(customerId);

        return deviceRepository.findByCustomerIdOrderByIdAsc(customerId)
                .stream()
                .map(DeviceResponse::from)
                .toList();
    }

    public DeviceResponse getDevice(
            Long customerId,
            Long deviceId) {

        requireCustomer(customerId);

        return DeviceResponse.from(
                requireDevice(customerId, deviceId)
        );
    }

    @Transactional
    public DeviceResponse createDevice(
            Long customerId,
            DeviceRequest request) {

        requireCustomer(customerId);

        Device device = new Device(
                customerId,
                request.type(),
                request.brand(),
                request.model(),
                request.serialNumber(),
                request.imei(),
                request.notes()
        );

        Device saved = deviceRepository.saveAndFlush(device);

        return DeviceResponse.from(saved);
    }

    @Transactional
    public DeviceResponse updateDevice(
            Long customerId,
            Long deviceId,
            DeviceRequest request) {

        requireCustomer(customerId);

        Device device = requireDevice(customerId, deviceId);

        device.updateDetails(
                request.type(),
                request.brand(),
                request.model(),
                request.serialNumber(),
                request.imei(),
                request.notes()
        );

        deviceRepository.flush();

        return DeviceResponse.from(device);
    }

    private void requireCustomer(Long customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Customer not found."
            );
        }
    }

    private Device requireDevice(
            Long customerId,
            Long deviceId) {

        return deviceRepository
                .findByIdAndCustomerId(deviceId, customerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Device not found for this customer."
                ));
    }
}