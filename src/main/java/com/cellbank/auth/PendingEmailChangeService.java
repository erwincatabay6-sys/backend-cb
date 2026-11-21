package com.cellbank.auth;

import java.time.Duration;
import java.time.Instant;

import com.cellbank.email.EmailService;

import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PendingEmailChangeService {

    private static final Duration CODE_LIFETIME = Duration.ofMinutes(10);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final int MAX_FAILED_ATTEMPTS = 5;

    private final PendingEmailChangeRepository pendingRepository;
    private final EmailChangeCodeService codeService;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final EmailVerificationTokenRepository verificationTokenRepository;

    public PendingEmailChangeService(
            PendingEmailChangeRepository pendingRepository,
            EmailChangeCodeService codeService,
            EmailService emailService,
            UserRepository userRepository,
            PasswordResetTokenRepository resetTokenRepository,
            EmailVerificationTokenRepository verificationTokenRepository) {

        this.pendingRepository = pendingRepository;
        this.codeService = codeService;
        this.emailService = emailService;
        this.userRepository = userRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.verificationTokenRepository = verificationTokenRepository;
    }

    @Transactional(readOnly = true)
    public PendingEmailChangeStatusResponse getStatus(Long userId) {
        Instant now = Instant.now();

        return pendingRepository.findById(userId)
                .map(change -> new PendingEmailChangeStatusResponse(
                        true,
                        maskEmail(change.getProposedEmail()),
                        change.getExpiresAt(),
                        change.getIssuedAt().plus(RESEND_COOLDOWN),
                        now
                ))
                .orElseGet(() -> new PendingEmailChangeStatusResponse(
                        false, null, null, null, now
                ));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void startChange(User user, String proposedEmail) {
        String address = proposedEmail.strip();
        Long userId = user.getId();

        var existing = pendingRepository.findById(userId);

        // Saving the same proposal again should not resend or reset attempts.
        if (existing.isPresent()
                && existing.get().getProposedEmail().equalsIgnoreCase(address)) {
            return;
        }

        Instant now = Instant.now();
        String code = codeService.generateCode();
        String codeHash = codeService.hashCodeForChange(userId, address, code);

        PendingEmailChange change = new PendingEmailChange(
                userId,
                address,
                codeHash,
                now,
                now.plus(CODE_LIFETIME)
        );

        pendingRepository.saveAndFlush(change);
        sendCode(address, code);
    }
    
    @Transactional
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public void cancelChange(Long userId) {
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Staff account was not found."
                ));

        pendingRepository.findById(userId)
                .ifPresent(pendingRepository::delete);
    }

    @Transactional
    public void resendCode(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Please sign in again."
                ));

        if (user.getStatus() != UserStatus.ACTIVE || !user.isEmailVerified()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "An active account with a verified email is required."
            );
        }

        PendingEmailChange change = pendingRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "There is no pending email change."
                ));

        Instant now = Instant.now();
        Instant resendAvailableAt = change.getIssuedAt().plus(RESEND_COOLDOWN);

        if (now.isBefore(resendAvailableAt)) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Please wait before requesting another code."
            );
        }

        String address = change.getProposedEmail();
        String code = codeService.generateCode();
        String codeHash = codeService.hashCodeForChange(userId, address, code);

        change.renewCode(codeHash, now, now.plus(CODE_LIFETIME));
        pendingRepository.saveAndFlush(change);
        sendCode(address, code);
    }

    @Transactional
    public boolean confirmCode(Long userId, String submittedCode) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Please sign in again."
                ));

        if (user.getStatus() != UserStatus.ACTIVE || !user.isEmailVerified()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "An active account with a verified email is required."
            );
        }

        PendingEmailChange change = pendingRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "There is no pending email change."
                ));

        Instant now = Instant.now();

        if (!now.isBefore(change.getExpiresAt())) {
            throw new ResponseStatusException(
                    HttpStatus.GONE,
                    "The confirmation code has expired. Request a new code."
            );
        }

        if (!codeService.matches(change, submittedCode)) {
            change.recordFailedAttempt();

            if (change.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
                pendingRepository.delete(change);
            }

            // Return normally so the transaction records the failed attempt.
            return false;
        }

        String proposedEmail = change.getProposedEmail();

        if (userRepository.existsByEmail(proposedEmail)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "That email address is already in use."
            );
        }

        user.setEmail(proposedEmail);
        userRepository.saveAndFlush(user);

        resetTokenRepository.findAllByUserIdAndUsedAtIsNull(userId)
                .forEach(token -> token.markUsed(now));

        verificationTokenRepository.findAllByUserIdAndUsedAtIsNull(userId)
                .forEach(token -> token.markUsed(now));

        pendingRepository.delete(change);
        return true;
    }

    private void sendCode(String address, String code) {
        try {
            emailService.sendEmailChangeCode(address, code);
        } catch (MailException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Unable to send the confirmation code. Please try again later.",
                    exception
            );
        }
    }

    private String maskEmail(String email) {
        int at = email.lastIndexOf('@');

        if (at < 1 || at == email.length() - 1) {
            return "***";
        }

        return email.substring(0, 1) + "***" + email.substring(at);
    }
}