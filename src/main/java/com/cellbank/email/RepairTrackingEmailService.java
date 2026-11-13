package com.cellbank.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RepairTrackingEmailService {

    private final EmailService emailService;
    private final String frontendUrl;

    public RepairTrackingEmailService(
            EmailService emailService,
            @Value("${cellbank.frontend-url:http://localhost:5173}")
            String frontendUrl) {

        this.emailService = emailService;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    public void sendRepairReceived(
            String recipient,
            String customerName,
            String repairReference,
            String deviceDescription,
            String trackingCode) {

        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException(
                    "A customer email address is required."
            );
        }

        String greetingName =
                customerName == null || customerName.isBlank()
                        ? "Customer"
                        : customerName.strip();

        String trackingLink = frontendUrl
                + "/track?code="
                + trackingCode;

        String subject = "Cellbank — Repair received · "
                + repairReference;

        String body = """
                Hello %s,

                We've received your device for repair. Keep this email so you can check its progress anytime.

                Repair reference: %s
                Device: %s
                Status at intake: Received
                Tracking code: %s

                Track your repair:
                %s

                Opening this link automatically fills in your tracking code and displays the latest repair status.

                If the link doesn't open, copy it into your browser. You can also enter your tracking code manually on the tracking page.

                Keep your tracking link and code private, as they provide access to your repair's tracking information.

                Thank you,
                Cellbank
                Repair Management System
                """.formatted(
                        greetingName,
                        repairReference,
                        deviceDescription,
                        trackingCode,
                        trackingLink
                );

        emailService.send(
                recipient.strip(),
                subject,
                body
        );
    }
}
