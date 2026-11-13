package com.cellbank.customer;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.cellbank.auth.CurrentUserService;
import com.cellbank.auth.CurrentUserResponse;

@Service
@Transactional(readOnly = true)
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final CustomerRepository customerRepository;
    private final CurrentUserService currentUserService;

    public DeviceService(
            DeviceRepository deviceRepository,
            CustomerRepository customerRepository,
            CurrentUserService currentUserService) {

        this.deviceRepository = deviceRepository;
        this.customerRepository = customerRepository;
        this.currentUserService = currentUserService;
    }

    public List<DeviceResponse> getDevices(
            String username,
            Long customerId) {

        var currentUser = currentUserService.getCurrentUser(username);
        boolean broadAccess = hasBroadDeviceAccess(currentUser);

        List<Device> devices;

        if (broadAccess) {
            requireCustomer(customerId);

            devices = deviceRepository
                    .findByCustomerIdOrderByIdAsc(customerId);
        } else {
            devices = deviceRepository
                    .findCustomerDevicesAssignedToTechnician(
                            customerId,
                            currentUser.id()
                    );

            if (devices.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Customer not found."
                );
            }
        }

        return devices.stream()
                .map(DeviceResponse::from)
                .toList();
    }

    public DeviceResponse getDevice(
            String username,
            Long customerId,
            Long deviceId) {

        var currentUser = currentUserService.getCurrentUser(username);
        boolean broadAccess = hasBroadDeviceAccess(currentUser);

        if (broadAccess) {
            requireCustomer(customerId);

            return DeviceResponse.from(
                    requireDevice(customerId, deviceId)
            );
        }

        Device device = deviceRepository.findAssignedDevice(
                        customerId,
                        deviceId,
                        currentUser.id()
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Device not found for this customer."
                ));

        return DeviceResponse.from(device);
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
    
    private boolean hasBroadDeviceAccess(CurrentUserResponse user) {
        boolean broadAccess = user.roles().contains("ADMIN")
                || user.roles().contains("FRONT_DESK");

        if (!broadAccess && !user.roles().contains("TECHNICIAN")) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not have permission to view devices."
            );
        }

        return broadAccess;
    }
}