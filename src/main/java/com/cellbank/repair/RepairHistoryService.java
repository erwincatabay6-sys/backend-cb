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
import com.cellbank.auth.CurrentUserService;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('ADMIN', 'FRONT_DESK', 'TECHNICIAN')")
public class RepairHistoryService {

    private final CustomerRepository customerRepository;
    private final DeviceRepository deviceRepository;
    private final RepairJobRepository repairJobRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final RepairAccessService repairAccessService;

    public RepairHistoryService(
            CustomerRepository customerRepository,
            DeviceRepository deviceRepository,
            RepairJobRepository repairJobRepository,
            UserRepository userRepository,
            CurrentUserService currentUserService,
            RepairAccessService repairAccessService) {

        this.customerRepository = customerRepository;
        this.deviceRepository = deviceRepository;
        this.repairJobRepository = repairJobRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.repairAccessService = repairAccessService;
    }

    public List<RepairResponse> getCustomerHistory(
            String username,
            Long customerId) {

        User actingUser = requireActingUser(username);
        requireCustomer(customerId);

        List<RepairJob> repairs = repairJobRepository
                .findCustomerRepairs(customerId)
                .stream()
                .filter(repair ->
                        repairAccessService.canAccessRepair(actingUser, repair))
                .toList();

        return toResponses(repairs, customerId);
    }

    public List<RepairResponse> getDeviceHistory(
            String username,
            Long customerId,
            Long deviceId) {

        User actingUser = requireActingUser(username);
        requireCustomer(customerId);

        deviceRepository.findByIdAndCustomerId(deviceId, customerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Device not found for this customer."
                ));

        List<RepairJob> repairs = repairJobRepository
                .findByDeviceIdOrderByCreatedAtDescIdDesc(deviceId)
                .stream()
                .filter(repair ->
                        repairAccessService.canAccessRepair(actingUser, repair))
                .toList();

        return toResponses(repairs, customerId);
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
    private User requireActingUser(String username) {
        Long userId = currentUserService
                .getCurrentUser(username)
                .id();

        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Your account could not be found."
                ));
    }
}