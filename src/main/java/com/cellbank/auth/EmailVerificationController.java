package com.cellbank.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.CacheControl;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    public EmailVerificationController(
            EmailVerificationService emailVerificationService) {

        this.emailVerificationService = emailVerificationService;
    }
    
    @GetMapping("/me/email-verification")
    public ResponseEntity<EmailVerificationStatusResponse> getStatus(
            Authentication authentication) {

        EmailVerificationStatusResponse status =
                emailVerificationService.getStatus(
                        authentication == null
                                ? null
                                : authentication.getName()
                );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(status);
    }

    @PostMapping("/me/email-verification")
    public ResponseEntity<Void> sendVerification(
            Authentication authentication) {

        emailVerificationService.sendVerification(
                authentication == null ? null : authentication.getName()
        );

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request) {

        emailVerificationService.verifyEmail(request.token());

        return ResponseEntity.noContent().build();
    }
}
