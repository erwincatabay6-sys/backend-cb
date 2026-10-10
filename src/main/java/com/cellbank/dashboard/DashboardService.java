package com.cellbank.dashboard;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.cellbank.auth.CurrentUserService;
import com.cellbank.auth.User;
import com.cellbank.auth.UserRepository;
import com.cellbank.auth.UserStatus;
import com.cellbank.customer.Customer;
import com.cellbank.customer.CustomerRepository;
import com.cellbank.customer.Device;
import com.cellbank.customer.DeviceRepository;
import com.cellbank.repair.RepairJob;
import com.cellbank.repair.RepairJobRepository;
import com.cellbank.repair.RepairPayment;
import com.cellbank.repair.RepairPaymentRepository;
import com.cellbank.repair.RepairStatus;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('ADMIN', 'FRONT_DESK', 'TECHNICIAN')")
public class DashboardService {

    private static final Set<RepairStatus> ATTENTION_STATUSES = Set.of(
            RepairStatus.RECEIVED,
            RepairStatus.AWAITING_APPROVAL,
            RepairStatus.AWAITING_PARTS,
            RepairStatus.READY_FOR_RELEASE
    );

    private final CurrentUserService currentUserService;
    private final RepairJobRepository repairRepository;
    private final RepairPaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final CustomerRepository customerRepository;

    public DashboardService(
            CurrentUserService currentUserService,
            RepairJobRepository repairRepository,
            RepairPaymentRepository paymentRepository,
            UserRepository userRepository,
            DeviceRepository deviceRepository,
            CustomerRepository customerRepository) {

        this.currentUserService = currentUserService;
        this.repairRepository = repairRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.deviceRepository = deviceRepository;
        this.customerRepository = customerRepository;
    }

    public DashboardResponse getDashboard(String username) {
        var currentUser = currentUserService.getCurrentUser(username);

        boolean isAdmin = currentUser.roles().contains("ADMIN");
        boolean isFrontDesk = currentUser.roles().contains("FRONT_DESK");
        boolean isTechnician = currentUser.roles().contains("TECHNICIAN");

        if (!isAdmin && !isFrontDesk && !isTechnician) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not have permission to view the dashboard."
            );
        }

        boolean technicianOnly =
                isTechnician && !isAdmin && !isFrontDesk;

        List<RepairJob> repairs = repairRepository.findAll(
                Sort.by(
                        Sort.Order.desc("updatedAt"),
                        Sort.Order.desc("id")
                )
        );

        if (technicianOnly) {
            repairs = repairs.stream()
                    .filter(repair -> Objects.equals(
                            repair.getAssignedTechnicianId(),
                            currentUser.id()
                    ))
                    .toList();
        }

        Map<RepairStatus, Long> statusCounts =
                new EnumMap<>(RepairStatus.class);

        for (RepairStatus status : RepairStatus.values()) {
            statusCounts.put(status, 0L);
        }

        Map<Long, Long> totalByTechnician = new HashMap<>();
        Map<Long, Long> activeByTechnician = new HashMap<>();

        long activeRepairs = 0;

        for (RepairJob repair : repairs) {
            statusCounts.merge(repair.getStatus(), 1L, Long::sum);

            Long technicianId = repair.getAssignedTechnicianId();

            if (technicianId != null) {
                totalByTechnician.merge(technicianId, 1L, Long::sum);
            }

            if (isActive(repair)) {
                activeRepairs++;

                if (technicianId != null) {
                    activeByTechnician.merge(technicianId, 1L, Long::sum);
                }
            }
        }

        var summary = new DashboardResponse.Summary(
                repairs.size(),
                activeRepairs,
                statusCounts.get(RepairStatus.READY_FOR_RELEASE),
                statusCounts.get(RepairStatus.COMPLETED)
        );

        List<RepairJob> attentionRepairs = repairs.stream()
                .filter(repair ->
                        ATTENTION_STATUSES.contains(repair.getStatus()))
                .toList();

        List<RepairJob> recentActiveRepairs = repairs.stream()
                .filter(DashboardService::isActive)
                .limit(5)
                .toList();

        // Load the labels needed by the dashboard lists in batches.
        Set<Long> deviceIds = new HashSet<>();
        Set<Long> technicianIds = new HashSet<>();

        for (RepairJob repair : attentionRepairs) {
            deviceIds.add(repair.getDeviceId());
        }

        for (RepairJob repair : recentActiveRepairs) {
            deviceIds.add(repair.getDeviceId());
        }

        for (RepairJob repair : repairs) {
            if (repair.getAssignedTechnicianId() != null) {
                technicianIds.add(repair.getAssignedTechnicianId());
            }
        }

        Map<Long, Device> devices = new HashMap<>();
        Set<Long> customerIds = new HashSet<>();

        for (Device device : deviceRepository.findAllById(deviceIds)) {
            devices.put(device.getId(), device);
            customerIds.add(device.getCustomerId());
        }

        Map<Long, Customer> customers = new HashMap<>();

        for (Customer customer :
                customerRepository.findAllById(customerIds)) {
            customers.put(customer.getId(), customer);
        }

        Map<Long, String> technicianNames = new HashMap<>();

        for (User technician :
                userRepository.findAllById(technicianIds)) {
            technicianNames.put(
                    technician.getId(),
                    technician.getFullName()
            );
        }

        List<DashboardResponse.TechnicianWorkload> workload;

        if (technicianOnly) {
            workload = List.of(
                    new DashboardResponse.TechnicianWorkload(
                            currentUser.id(),
                            currentUser.name(),
                            activeByTechnician.getOrDefault(
                                    currentUser.id(), 0L),
                            totalByTechnician.getOrDefault(
                                    currentUser.id(), 0L)
                    )
            );
        } else {
            workload = userRepository
                    .findDistinctByStatusAndRoles_NameOrderByFullNameAscIdAsc(
                            UserStatus.ACTIVE,
                            "TECHNICIAN"
                    )
                    .stream()
                    .map(technician ->
                            new DashboardResponse.TechnicianWorkload(
                                    technician.getId(),
                                    technician.getFullName(),
                                    activeByTechnician.getOrDefault(
                                            technician.getId(), 0L),
                                    totalByTechnician.getOrDefault(
                                            technician.getId(), 0L)
                            ))
                    .toList();
        }

        return new DashboardResponse(
                technicianOnly,
                summary,
                statusCounts,
                attentionRepairs.stream()
                        .map(repair -> toRepairItem(
                                repair, devices, customers, technicianNames))
                        .toList(),
                workload,
                recentActiveRepairs.stream()
                        .map(repair -> toRepairItem(
                                repair, devices, customers, technicianNames))
                        .toList(),
                isAdmin ? calculateFinancialSnapshot(repairs) : null
        );
    }

    private DashboardResponse.RepairItem toRepairItem(
            RepairJob repair,
            Map<Long, Device> devices,
            Map<Long, Customer> customers,
            Map<Long, String> technicianNames) {

        Device device = devices.get(repair.getDeviceId());

        Customer customer = device == null
                ? null
                : customers.get(device.getCustomerId());

        String deviceName = device == null
                ? "Unknown device"
                : device.getBrand() + " " + device.getModel();

        String technicianName =
                repair.getAssignedTechnicianId() == null
                        ? "Unassigned"
                        : technicianNames.getOrDefault(
                                repair.getAssignedTechnicianId(),
                                "Unknown technician"
                        );

        return new DashboardResponse.RepairItem(
                repair.getId(),
                repair.getRepairReference(),
                customer == null ? "Unknown customer" : customer.getName(),
                deviceName,
                technicianName,
                repair.getStatus()
        );
    }

    private DashboardResponse.FinancialSnapshot calculateFinancialSnapshot(
            List<RepairJob> repairs) {

        Map<Long, BigDecimal> paidByRepair = new HashMap<>();
        BigDecimal totalCollected = BigDecimal.ZERO;

        for (RepairPayment payment : paymentRepository.findAll()) {
            paidByRepair.merge(
                    payment.getRepairJobId(),
                    payment.getAmount(),
                    BigDecimal::add
            );

            totalCollected = totalCollected.add(payment.getAmount());
        }

        BigDecimal totalAgreedValue = BigDecimal.ZERO;
        BigDecimal outstandingBalance = BigDecimal.ZERO;

        for (RepairJob repair : repairs) {
            BigDecimal agreedPrice = repair.getAgreedPrice();

            if (agreedPrice == null) {
                continue;
            }

            totalAgreedValue = totalAgreedValue.add(agreedPrice);

            BigDecimal paid = paidByRepair.getOrDefault(
                    repair.getId(),
                    BigDecimal.ZERO
            );

            BigDecimal balance = agreedPrice
                    .subtract(paid)
                    .max(BigDecimal.ZERO);

            outstandingBalance = outstandingBalance.add(balance);
        }

        return new DashboardResponse.FinancialSnapshot(
                totalAgreedValue,
                totalCollected,
                outstandingBalance
        );
    }

    private static boolean isActive(RepairJob repair) {
        return repair.getStatus() != RepairStatus.COMPLETED
                && repair.getStatus() != RepairStatus.CANCELLED;
    }
}