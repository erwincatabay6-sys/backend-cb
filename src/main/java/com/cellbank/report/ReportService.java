package com.cellbank.report;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.cellbank.auth.CurrentUserService;
import com.cellbank.auth.User;
import com.cellbank.auth.UserRepository;
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
@PreAuthorize("hasRole('ADMIN')")
public class ReportService {

    private static final ZoneId REPORT_ZONE =
            ZoneId.of("Asia/Manila");

    private final CurrentUserService currentUserService;
    private final RepairJobRepository repairRepository;
    private final RepairPaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final CustomerRepository customerRepository;

    public ReportService(
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

    public ReportResponse getReport(
            String username,
            LocalDate startDate,
            LocalDate endDate) {

        var currentUser = currentUserService.getCurrentUser(username);

        if (!currentUser.roles().contains("ADMIN")) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not have permission to view reports."
            );
        }

        validateDates(startDate, endDate);

        Instant start = startDate == null
                ? null
                : startDate.atStartOfDay(REPORT_ZONE).toInstant();

        Instant end = endDate == null
                ? null
                : endDate.plusDays(1)
                        .atStartOfDay(REPORT_ZONE)
                        .toInstant();

        List<RepairJob> repairs = loadRepairs(start, end);
        List<RepairPayment> payments = loadPayments(start, end);

        Map<Long, Device> devices = new HashMap<>();

        List<Long> deviceIds = repairs.stream()
                .map(RepairJob::getDeviceId)
                .distinct()
                .toList();

        for (Device device : deviceRepository.findAllById(deviceIds)) {
            devices.put(device.getId(), device);
        }

        Map<Long, Customer> customers = new HashMap<>();

        List<Long> customerIds = devices.values().stream()
                .map(Device::getCustomerId)
                .distinct()
                .toList();

        for (Customer customer :
                customerRepository.findAllById(customerIds)) {
            customers.put(customer.getId(), customer);
        }

        // Include current technician accounts and any staff referenced
        // by the selected repairs, even if their role later changed.
        Map<Long, User> technicians = new HashMap<>();

        for (User technician : userRepository
                .findDistinctByRoles_NameOrderByFullNameAscIdAsc(
                        "TECHNICIAN")) {
            technicians.put(technician.getId(), technician);
        }

        List<Long> assignedIds = repairs.stream()
                .map(RepairJob::getAssignedTechnicianId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        for (User technician : userRepository.findAllById(assignedIds)) {
            technicians.put(technician.getId(), technician);
        }

        Map<Long, BigDecimal> paidByRepair = new HashMap<>();

        if (!repairs.isEmpty()) {
            List<Long> repairIds = repairs.stream()
                    .map(RepairJob::getId)
                    .toList();

            for (var total :
                    paymentRepository.sumPaymentsByRepairIds(repairIds)) {
                paidByRepair.put(
                        total.getRepairJobId(),
                        total.getTotalPaid()
                );
            }
        }

        Map<RepairStatus, Long> statusCounts =
                new EnumMap<>(RepairStatus.class);

        for (RepairStatus status : RepairStatus.values()) {
            statusCounts.put(status, 0L);
        }

        Map<String, Long> monthlyRepairs = new TreeMap<>();
        Map<String, BigDecimal> monthlyPayments = new TreeMap<>();

        Map<Long, Long> technicianTotals = new HashMap<>();
        Map<Long, Long> technicianActive = new HashMap<>();
        Map<Long, Long> technicianCompleted = new HashMap<>();

        List<ReportResponse.RepairRecord> repairRecords =
                new ArrayList<>();

        List<ReportResponse.FinancialRecord> financialRecords =
                new ArrayList<>();

        long activeRepairs = 0;
        long paidRepairs = 0;
        long partiallyPaidRepairs = 0;
        long unpaidRepairs = 0;
        long priceNotAgreedRepairs = 0;

        BigDecimal totalAgreedValue = BigDecimal.ZERO;
        BigDecimal totalOutstandingBalance = BigDecimal.ZERO;
        BigDecimal totalPaymentsCollected = BigDecimal.ZERO;

        for (RepairJob repair : repairs) {
            RepairStatus status = repair.getStatus();
            boolean active = isActive(status);

            statusCounts.merge(status, 1L, Long::sum);

            if (active) {
                activeRepairs++;
            }

            LocalDate receivedDate = repair.getCreatedAt()
                    .atZone(REPORT_ZONE)
                    .toLocalDate();

            monthlyRepairs.merge(
                    receivedDate.toString().substring(0, 7),
                    1L,
                    Long::sum
            );

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

            Long technicianId = repair.getAssignedTechnicianId();
            String technicianName = "Unassigned";

            if (technicianId != null) {
                User technician = technicians.get(technicianId);

                if (technician == null) {
                    throw new IllegalStateException(
                            "A repair references a missing technician."
                    );
                }

                technicianName = technician.getFullName();

                technicianTotals.merge(technicianId, 1L, Long::sum);

                if (active) {
                    technicianActive.merge(technicianId, 1L, Long::sum);
                }

                if (status == RepairStatus.COMPLETED) {
                    technicianCompleted.merge(
                            technicianId, 1L, Long::sum);
                }
            }

            repairRecords.add(new ReportResponse.RepairRecord(
                    repair.getId(),
                    repair.getRepairReference(),
                    receivedDate,
                    customer.getName(),
                    device.getBrand() + " " + device.getModel(),
                    technicianId,
                    technicianName,
                    status
            ));

            BigDecimal agreedPrice = repair.getAgreedPrice();

            BigDecimal totalPaid = paidByRepair.getOrDefault(
                    repair.getId(),
                    BigDecimal.ZERO
            );

            BigDecimal balance = null;
            String paymentStatus;

            if (agreedPrice == null) {
                paymentStatus = "Price Not Agreed";
                priceNotAgreedRepairs++;
            } else {
                totalAgreedValue = totalAgreedValue.add(agreedPrice);

                balance = agreedPrice.subtract(totalPaid)
                        .max(BigDecimal.ZERO);

                totalOutstandingBalance =
                        totalOutstandingBalance.add(balance);

                if (totalPaid.compareTo(agreedPrice) >= 0) {
                    paymentStatus = "Paid";
                    paidRepairs++;
                } else if (totalPaid.signum() > 0) {
                    paymentStatus = "Partially Paid";
                    partiallyPaidRepairs++;
                } else {
                    paymentStatus = "Unpaid";
                    unpaidRepairs++;
                }
            }

            financialRecords.add(new ReportResponse.FinancialRecord(
                    repair.getId(),
                    repair.getRepairReference(),
                    customer.getName(),
                    agreedPrice,
                    totalPaid,
                    balance,
                    paymentStatus
            ));
        }

        // Payments are filtered by their own recorded date,
        // regardless of when the related repair was received.
        for (RepairPayment payment : payments) {
            String month = payment.getPaidAt()
                    .atZone(REPORT_ZONE)
                    .toLocalDate()
                    .toString()
                    .substring(0, 7);

            monthlyPayments.merge(
                    month,
                    payment.getAmount(),
                    BigDecimal::add
            );

            totalPaymentsCollected =
                    totalPaymentsCollected.add(payment.getAmount());
        }

        List<ReportResponse.TechnicianActivity> technicianActivity =
                technicians.values().stream()
                        .sorted(
                                java.util.Comparator
                                        .comparing(
                                                User::getFullName,
                                                String.CASE_INSENSITIVE_ORDER
                                        )
                                        .thenComparing(User::getId)
                        )
                        .map(technician ->
                                new ReportResponse.TechnicianActivity(
                                        technician.getId(),
                                        technician.getFullName(),
                                        technicianTotals.getOrDefault(
                                                technician.getId(), 0L),
                                        technicianActive.getOrDefault(
                                                technician.getId(), 0L),
                                        technicianCompleted.getOrDefault(
                                                technician.getId(), 0L)
                                ))
                        .toList();

        return new ReportResponse(
                startDate,
                endDate,
                REPORT_ZONE.getId(),
                new ReportResponse.RepairSummary(
                        repairs.size(),
                        activeRepairs,
                        statusCounts.get(RepairStatus.COMPLETED),
                        statusCounts.get(RepairStatus.CANCELLED)
                ),
                statusCounts,
                monthlyRepairs.entrySet().stream()
                        .map(entry -> new ReportResponse.RepairVolumePoint(
                                entry.getKey(),
                                entry.getValue()
                        ))
                        .toList(),
                new ReportResponse.FinancialSummary(
                        totalAgreedValue,
                        totalPaymentsCollected,
                        totalOutstandingBalance,
                        paidRepairs,
                        partiallyPaidRepairs,
                        unpaidRepairs,
                        priceNotAgreedRepairs
                ),
                monthlyPayments.entrySet().stream()
                        .map(entry -> new ReportResponse.PaymentTrendPoint(
                                entry.getKey(),
                                entry.getValue()
                        ))
                        .toList(),
                financialRecords,
                technicianActivity,
                repairRecords
        );
    }

    private List<RepairJob> loadRepairs(Instant start, Instant end) {
        if (start != null && end != null) {
            return repairRepository
                    .findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
                            start, end);
        }

        if (start != null) {
            return repairRepository
                    .findByCreatedAtGreaterThanEqualOrderByCreatedAtDescIdDesc(
                            start);
        }

        if (end != null) {
            return repairRepository
                    .findByCreatedAtLessThanOrderByCreatedAtDescIdDesc(end);
        }

        return repairRepository.findAll(
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );
    }

    private List<RepairPayment> loadPayments(Instant start, Instant end) {
        if (start != null && end != null) {
            return paymentRepository
                    .findByPaidAtGreaterThanEqualAndPaidAtLessThanOrderByPaidAtAscIdAsc(
                            start, end);
        }

        if (start != null) {
            return paymentRepository
                    .findByPaidAtGreaterThanEqualOrderByPaidAtAscIdAsc(start);
        }

        if (end != null) {
            return paymentRepository
                    .findByPaidAtLessThanOrderByPaidAtAscIdAsc(end);
        }

        return paymentRepository.findAll(
                Sort.by(
                        Sort.Order.asc("paidAt"),
                        Sort.Order.asc("id")
                )
        );
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if ((startDate != null
                && (startDate.getYear() < 1 || startDate.getYear() > 9999))
                || (endDate != null
                && (endDate.getYear() < 1 || endDate.getYear() > 9999))) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Report dates must use a year between 1 and 9999."
            );
        }

        if (startDate != null
                && endDate != null
                && startDate.isAfter(endDate)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Start date must not be after end date."
            );
        }
    }

    private static boolean isActive(RepairStatus status) {
        return status != RepairStatus.COMPLETED
                && status != RepairStatus.CANCELLED;
    }
}