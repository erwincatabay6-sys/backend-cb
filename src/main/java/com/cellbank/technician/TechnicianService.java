package com.cellbank.technician;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cellbank.auth.CurrentUserService;
import com.cellbank.auth.User;
import com.cellbank.auth.UserRepository;
import com.cellbank.customer.Customer;
import com.cellbank.customer.CustomerRepository;
import com.cellbank.customer.Device;
import com.cellbank.customer.DeviceRepository;
import com.cellbank.repair.RepairJob;
import com.cellbank.repair.RepairJobRepository;
import com.cellbank.repair.RepairStatus;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('ADMIN', 'FRONT_DESK', 'TECHNICIAN')")
public class TechnicianService {

    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;
    private final RepairJobRepository repairRepository;
    private final DeviceRepository deviceRepository;
    private final CustomerRepository customerRepository;

    public TechnicianService(
            CurrentUserService currentUserService,
            UserRepository userRepository,
            RepairJobRepository repairRepository,
            DeviceRepository deviceRepository,
            CustomerRepository customerRepository) {

        this.currentUserService = currentUserService;
        this.userRepository = userRepository;
        this.repairRepository = repairRepository;
        this.deviceRepository = deviceRepository;
        this.customerRepository = customerRepository;
    }

    public List<TechnicianResponse> getTechnicians(String username) {
        requireAccess(username);

        List<User> technicians = userRepository
                .findDistinctByRoles_NameOrderByFullNameAscIdAsc(
                        "TECHNICIAN"
                );

        Map<Long, Long> totalCounts = new HashMap<>();
        Map<Long, Long> activeCounts = new HashMap<>();

        for (var count :
                repairRepository.countRepairsByTechnicianAndStatus()) {

            totalCounts.merge(
                    count.getTechnicianId(),
                    count.getRepairCount(),
                    Long::sum
            );

            if (isActive(count.getStatus())) {
                activeCounts.merge(
                        count.getTechnicianId(),
                        count.getRepairCount(),
                        Long::sum
                );
            }
        }

        return technicians.stream()
                .map(technician -> toResponse(
                        technician,
                        activeCounts.getOrDefault(technician.getId(), 0L),
                        totalCounts.getOrDefault(technician.getId(), 0L)
                ))
                .toList();
    }

    public TechnicianDetailsResponse getTechnician(
            String username,
            Long technicianId) {

        requireAccess(username);

        User technician = userRepository
                .findByIdAndRoles_Name(technicianId, "TECHNICIAN")
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Technician not found."
                ));

        List<RepairJob> repairs = repairRepository
                .findByAssignedTechnicianIdOrderByUpdatedAtDescIdDesc(
                        technicianId
                );

        Map<RepairStatus, Long> statusCounts =
                new EnumMap<>(RepairStatus.class);

        for (RepairStatus status : RepairStatus.values()) {
            statusCounts.put(status, 0L);
        }

        List<Long> deviceIds = repairs.stream()
                .map(RepairJob::getDeviceId)
                .distinct()
                .toList();

        Map<Long, Device> devices = new HashMap<>();

        for (Device device : deviceRepository.findAllById(deviceIds)) {
            devices.put(device.getId(), device);
        }

        List<Long> customerIds = devices.values().stream()
                .map(Device::getCustomerId)
                .distinct()
                .toList();

        Map<Long, Customer> customers = new HashMap<>();

        for (Customer customer :
                customerRepository.findAllById(customerIds)) {
            customers.put(customer.getId(), customer);
        }

        List<TechnicianDetailsResponse.AssignedRepair> activeRepairs =
                new ArrayList<>();

        List<TechnicianDetailsResponse.AssignedRepair> repairHistory =
                new ArrayList<>();

        for (RepairJob repair : repairs) {
            statusCounts.merge(repair.getStatus(), 1L, Long::sum);

            var item = toAssignedRepair(repair, devices, customers);

            if (isActive(repair.getStatus())) {
                activeRepairs.add(item);
            } else {
                repairHistory.add(item);
            }
        }

        return new TechnicianDetailsResponse(
                toResponse(
                        technician,
                        activeRepairs.size(),
                        repairs.size()
                ),
                statusCounts,
                activeRepairs,
                repairHistory
        );
    }

    private TechnicianResponse toResponse(
            User technician,
            long activeRepairs,
            long totalRepairs) {

        return new TechnicianResponse(
                technician.getId(),
                technician.getFullName(),
                "Technician",
                technician.getStatus(),
                activeRepairs,
                totalRepairs
        );
    }

    private TechnicianDetailsResponse.AssignedRepair toAssignedRepair(
            RepairJob repair,
            Map<Long, Device> devices,
            Map<Long, Customer> customers) {

        Device device = devices.get(repair.getDeviceId());

        if (device == null) {
            throw new IllegalStateException(
                    "A repair references a missing device."
            );
        }

        Customer customer = customers.get(device.getCustomerId());

        if (customer == null) {
            throw new IllegalStateException(
                    "A device references a missing customer."
            );
        }

        return new TechnicianDetailsResponse.AssignedRepair(
                repair.getId(),
                repair.getRepairReference(),
                customer.getName(),
                device.getBrand() + " " + device.getModel(),
                repair.getStatus()
        );
    }

    private void requireAccess(String username) {
        var currentUser = currentUserService.getCurrentUser(username);

        boolean allowed = currentUser.roles().stream()
                .anyMatch(role ->
                        role.equals("ADMIN")
                                || role.equals("FRONT_DESK")
                                || role.equals("TECHNICIAN")
                );

        if (!allowed) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not have permission to view technicians."
            );
        }
    }

    private static boolean isActive(RepairStatus status) {
        return status != RepairStatus.COMPLETED
                && status != RepairStatus.CANCELLED;
    }
}
