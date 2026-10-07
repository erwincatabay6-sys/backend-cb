package com.cellbank.repair;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.cellbank.auth.CurrentUserService;
import com.cellbank.auth.User;
import com.cellbank.auth.UserRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN', 'FRONT_DESK')")
public class RepairPaymentService {

    private final RepairPaymentRepository paymentRepository;
    private final RepairJobRepository repairJobRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    @PersistenceContext
    private EntityManager entityManager;

    public RepairPaymentService(
            RepairPaymentRepository paymentRepository,
            RepairJobRepository repairJobRepository,
            UserRepository userRepository,
            CurrentUserService currentUserService) {

        this.paymentRepository = paymentRepository;
        this.repairJobRepository = repairJobRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
    }

    public List<RepairPaymentResponse> getPayments(Long repairId) {

        if (!repairJobRepository.existsById(repairId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Repair not found."
            );
        }

        List<RepairPayment> payments = paymentRepository
                .findByRepairJobIdOrderByPaidAtDescIdDesc(repairId);

        if (payments.isEmpty()) {
            return List.of();
        }

        List<Long> staffIds = payments.stream()
                .map(RepairPayment::getRecordedById)
                .distinct()
                .toList();

        Map<Long, String> staffNames = new HashMap<>();

        for (User staff : userRepository.findAllById(staffIds)) {
            staffNames.put(staff.getId(), staff.getFullName());
        }

        return payments.stream()
                .map(payment -> RepairPaymentResponse.from(
                        payment,
                        staffNames.get(payment.getRecordedById())
                ))
                .toList();
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'FRONT_DESK')")
    public RepairPaymentResponse createPayment(
            String username,
            Long repairId,
            RepairPaymentCreateRequest request) {

        Long recordedById = currentUserService
                .getCurrentUser(username)
                .id();

        User actingUser = userRepository.findById(recordedById)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Your account could not be found."
                ));

        boolean permittedByRole = actingUser.getRoles().stream()
                .anyMatch(role ->
                        "ADMIN".equals(role.getName())
                                || "FRONT_DESK".equals(role.getName()));

        if (!permittedByRole) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not have permission to record payments."
            );
        }

        RepairJob repair = repairJobRepository
                .findByIdForUpdate(repairId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Repair not found."
                ));

        entityManager.refresh(repair);

        if (!Objects.equals(
                repair.getUpdatedAt(),
                request.expectedUpdatedAt())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This repair changed since you opened it. "
                            + "Reload the page before recording a payment."
            );
        }

        if (repair.getStatus() == RepairStatus.COMPLETED
                || repair.getStatus() == RepairStatus.CANCELLED) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Payments cannot be added to a completed "
                            + "or cancelled repair."
            );
        }

        BigDecimal agreedPrice = repair.getAgreedPrice();

        if (agreedPrice == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Record the agreed repair price before "
                            + "recording a payment."
            );
        }

        BigDecimal amount = request.amount();

        if (amount == null || amount.signum() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payment amount must be greater than zero."
            );
        }

        BigDecimal totalPaid = paymentRepository
                .sumAmountByRepairJobId(repairId);

        BigDecimal balance = agreedPrice.subtract(totalPaid);

        if (balance.signum() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This repair has no remaining balance."
            );
        }

        if (amount.compareTo(balance) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payment amount cannot exceed the remaining balance of "
                            + balance.toPlainString() + "."
            );
        }

        RepairPayment payment = new RepairPayment(
                repair.getId(),
                recordedById,
                amount,
                request.note()
        );

        repair.markPaymentRecorded();

        RepairPayment saved = paymentRepository.saveAndFlush(payment);

        entityManager.refresh(saved);

        return RepairPaymentResponse.from(
                saved,
                actingUser.getFullName()
        );
    }
}