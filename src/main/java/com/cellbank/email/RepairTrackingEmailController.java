package com.cellbank.email;

import java.security.Principal;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repairs")
public class RepairTrackingEmailController {

    private final RepairTrackingEmailResendService resendService;

    public RepairTrackingEmailController(
            RepairTrackingEmailResendService resendService) {

        this.resendService = resendService;
    }

    @PostMapping("/{repairId}/tracking-email")
    public ResponseEntity<Void> resendTrackingEmail(
            Principal principal,
            @PathVariable("repairId") Long repairId) {

        resendService.resend(principal.getName(), repairId);

        return ResponseEntity.noContent().build();
    }
}
