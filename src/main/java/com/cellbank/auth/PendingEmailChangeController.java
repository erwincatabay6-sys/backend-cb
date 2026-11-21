package com.cellbank.auth;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth/me/pending-email-change")
public class PendingEmailChangeController {

    private final CurrentUserService currentUserService;
    private final PendingEmailChangeService pendingEmailChangeService;

    public PendingEmailChangeController(
            CurrentUserService currentUserService,
            PendingEmailChangeService pendingEmailChangeService) {

        this.currentUserService = currentUserService;
        this.pendingEmailChangeService = pendingEmailChangeService;
    }

    @GetMapping
    public PendingEmailChangeStatusResponse getStatus(Authentication authentication) {
        return pendingEmailChangeService.getStatus(currentUserId(authentication));
    }

    @PostMapping("/resend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resendCode(Authentication authentication) {
        pendingEmailChangeService.resendCode(currentUserId(authentication));
    }

    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmCode(
            Authentication authentication,
            @Valid @RequestBody ConfirmEmailChangeRequest request) {

        boolean confirmed = pendingEmailChangeService.confirmCode(
                currentUserId(authentication),
                request.code()
        );

        if (!confirmed) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Incorrect confirmation code."
            );
        }
    }

    private Long currentUserId(Authentication authentication) {
        return currentUserService.getCurrentUser(authentication.getName()).id();
    }
}