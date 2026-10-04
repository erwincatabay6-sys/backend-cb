package com.cellbank.repair;

import java.util.List;

import com.cellbank.customer.CustomerRepository;
import com.cellbank.customer.DeviceRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('ADMIN', 'FRONT_DESK', 'TECHNICIAN')")
public class RepairHistoryService {

    private final CustomerRepository customerRepository;
    private final DeviceRepository deviceRepository;
    private final RepairJobRepository repairJobRepository;

    public RepairHistoryService(
            CustomerRepository customerRepository,
            DeviceRepository deviceRepository,
            RepairJobRepository repairJobRepository) {

        this.customerRepository = customerRepository;
        this.deviceRepository = deviceRepository;
        this.repairJobRepository = repairJobRepository;
    }

    public List<RepairResponse> getCustomerHistory(Long customerId) {
        requireCustomer(customerId);

        return repairJobRepository.findCustomerRepairs(customerId)
                .stream()
                .map(repair -> RepairResponse.from(repair, customerId))
                .toList();
    }

    public List<RepairResponse> getDeviceHistory(
            Long customerId,
            Long deviceId) {

        requireCustomer(customerId);

        deviceRepository.findByIdAndCustomerId(deviceId, customerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Device not found for this customer."
                ));

        return repairJobRepository
                .findByDeviceIdOrderByCreatedAtDescIdDesc(deviceId)
                .stream()
                .map(repair -> RepairResponse.from(repair, customerId))
                .toList();
    }

    private void requireCustomer(Long customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Customer not found."
            );
        }
    }
}
