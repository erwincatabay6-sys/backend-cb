package com.cellbank.email;

import java.time.Duration;

import com.cellbank.auth.AuthRateLimitService;
import com.cellbank.auth.CurrentUserService;
import com.cellbank.customer.CustomerRepository;
import com.cellbank.customer.DeviceRepository;
import com.cellbank.repair.RepairJobRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpHeaders;

@Service
public class RepairTrackingEmailResendService {

    private final CurrentUserService currentUserService;
    private final RepairJobRepository repairRepository;
    private final DeviceRepository deviceRepository;
    private final CustomerRepository customerRepository;
    private final AuthRateLimitService rateLimitService;
    private final RepairTrackingEmailService emailService;
    private final TransactionTemplate readTransaction;

    public RepairTrackingEmailResendService(
            CurrentUserService currentUserService,
            RepairJobRepository repairRepository,
            DeviceRepository deviceRepository,
            CustomerRepository customerRepository,
            AuthRateLimitService rateLimitService,
            RepairTrackingEmailService emailService,
            PlatformTransactionManager transactionManager) {

        this.currentUserService = currentUserService;
        this.repairRepository = repairRepository;
        this.deviceRepository = deviceRepository;
        this.customerRepository = customerRepository;
        this.rateLimitService = rateLimitService;
        this.emailService = emailService;

        this.readTransaction =
                new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
        this.readTransaction.setIsolationLevel(
                TransactionDefinition.ISOLATION_REPEATABLE_READ
        );
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'FRONT_DESK')")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void resend(String username, Long repairId) {
        RepairCreatedEmailEvent details = readTransaction.execute(
                status -> loadDetails(username, repairId)
        );

        if (details == null) {
            throw new IllegalStateException(
                    "Tracking email details could not be loaded."
            );
        }

        long retryAfter = rateLimitService.checkAndConsume(
                "tracking-email:resend:repair:" + repairId,
                1,
                Duration.ofMinutes(1)
        );

        if (retryAfter > 0) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Please wait before resending this tracking email."
            ) {
                @Override
                public HttpHeaders getHeaders() {
                    HttpHeaders headers = new HttpHeaders();
                    headers.set(
                            "Retry-After",
                            Long.toString(retryAfter)
                    );
                    return headers;
                }
            };
        }

        try {
            emailService.sendRepairReceived(
                    details.recipient(),
                    details.customerName(),
                    details.repairReference(),
                    details.deviceDescription(),
                    details.trackingCode()
            );
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Email delivery could not be confirmed. "
                            + "The repair remains saved. "
                            + "Check the inbox before trying again later."
            );
        }
    }

    private RepairCreatedEmailEvent loadDetails(
            String username,
            Long repairId) {

        currentUserService.getCurrentUser(username);

        var repair = repairRepository.findById(repairId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Repair not found."
                ));

        var device = deviceRepository.findById(repair.getDeviceId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Device not found."
                ));

        var customer = customerRepository
                .findById(device.getCustomerId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Customer not found."
                ));

        if (customer.getEmail() == null
                || customer.getEmail().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Add an email address to the customer record "
                            + "before sending a tracking email."
            );
        }

        return new RepairCreatedEmailEvent(
                repair.getId(),
                customer.getEmail(),
                customer.getName(),
                repair.getRepairReference(),
                device.getBrand() + " " + device.getModel(),
                repair.getTrackingCode()
        );
    }
}