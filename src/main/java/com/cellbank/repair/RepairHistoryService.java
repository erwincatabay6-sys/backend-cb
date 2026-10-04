package com.cellbank.repair;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cellbank.auth.User;
import com.cellbank.auth.UserRepository;
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
    private final UserRepository userRepository;

    public RepairHistoryService(
            CustomerRepository customerRepository,
            DeviceRepository deviceRepository,
            RepairJobRepository repairJobRepository,
            UserRepository userRepository) {

        this.customerRepository = customerRepository;
        this.deviceRepository = deviceRepository;
        this.repairJobRepository = repairJobRepository;
        this.userRepository = userRepository;
    }

    public List<RepairResponse> getCustomerHistory(Long customerId) {
        requireCustomer(customerId);

        return toResponses(
                repairJobRepository.findCustomerRepairs(customerId),
                customerId
        );
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

        return toResponses(
                repairJobRepository
                        .findByDeviceIdOrderByCreatedAtDescIdDesc(deviceId),
                customerId
        );
    }

    private List<RepairResponse> toResponses(
            List<RepairJob> repairs,
            Long customerId) {

        if (repairs.isEmpty()) {
            return List.of();
        }

        Set<Long> staffIds = new HashSet<>();

        for (RepairJob repair : repairs) {
            staffIds.add(repair.getCreatedById());

            if (repair.getAssignedTechnicianId() != null) {
                staffIds.add(repair.getAssignedTechnicianId());
            }
        }

        Map<Long, String> staffNames = new HashMap<>();

        for (User staff : userRepository.findAllById(staffIds)) {
            staffNames.put(staff.getId(), staff.getFullName());
        }

        return repairs.stream()
                .map(repair -> RepairResponse.from(
                        repair,
                        customerId,
                        staffNames.get(repair.getAssignedTechnicianId()),
                        staffNames.get(repair.getCreatedById())
                ))
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