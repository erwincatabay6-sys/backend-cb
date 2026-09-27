package com.cellbank.auth;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class PasswordRecoveryController {

    private final PasswordRecoveryService passwordRecoveryService;

    public PasswordRecoveryController(
            PasswordRecoveryService passwordRecoveryService) {

        this.passwordRecoveryService = passwordRecoveryService;
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> requestRecovery(
            @Valid @RequestBody ForgotPasswordRequest request) {

        passwordRecoveryService.requestRecovery(request);

        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {

        passwordRecoveryService.resetPassword(request);

        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
