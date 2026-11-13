package com.cellbank.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class RepairCreatedEmailListener {

    private static final Logger log =
            LoggerFactory.getLogger(RepairCreatedEmailListener.class);

    private final RepairTrackingEmailService trackingEmailService;

    public RepairCreatedEmailListener(
            RepairTrackingEmailService trackingEmailService) {

        this.trackingEmailService = trackingEmailService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRepairCreated(RepairCreatedEmailEvent event) {
        if (event.recipient() == null
                || event.recipient().isBlank()) {
            return;
        }
        

        try {
            trackingEmailService.sendRepairReceived(
                    event.recipient(),
                    event.customerName(),
                    event.repairReference(),
                    event.deviceDescription(),
                    event.trackingCode()
            );
        } catch (RuntimeException exception) {
            // Do not log customer addresses, tracking codes,
            // email bodies, or SMTP exception messages.
            log.warn(
                    "Tracking email failed for repair {}. Failure type: {}",
                    event.repairId(),
                    exception.getClass().getSimpleName()
            );
        }
    }
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRepairReady(RepairReadyEmailEvent event) {
        if (event.recipient() == null
                || event.recipient().isBlank()) {
            return;
        }

        try {
            trackingEmailService.sendReadyForRelease(
                    event.recipient(),
                    event.customerName(),
                    event.repairReference(),
                    event.deviceDescription(),
                    event.trackingCode(),
                    event.customerUpdate()
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "Ready-for-release email failed for repair {}. Failure type: {}",
                    event.repairId(),
                    exception.getClass().getSimpleName()
            );
        }
    }
}
