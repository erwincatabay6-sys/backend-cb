package com.cellbank.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.server.ResponseStatusException;

import com.cellbank.email.EmailService;

import jakarta.persistence.EntityManager;

@Service
public class PasswordRecoveryService {

    private static final Logger log =
            LoggerFactory.getLogger(PasswordRecoveryService.class);

    private static final Duration TOKEN_LIFETIME =
            Duration.ofMinutes(30);

    private static final Duration RESEND_COOLDOWN =
            Duration.ofSeconds(60);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final SecureTokenService secureTokenService;
    private final EmailService emailService;
    private final EntityManager entityManager;
    private final PasswordEncoder passwordEncoder;
    private final SessionInvalidationService sessionInvalidationService;
    private final String frontendUrl;

    public PasswordRecoveryService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            SecureTokenService secureTokenService,
            EmailService emailService,
            EntityManager entityManager,
            PasswordEncoder passwordEncoder,
            SessionInvalidationService sessionInvalidationService,
            @Value("${cellbank.frontend-url:http://localhost:5173}")
            String frontendUrl) {

        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.secureTokenService = secureTokenService;
        this.emailService = emailService;
        this.entityManager = entityManager;
        this.passwordEncoder = passwordEncoder;
        this.sessionInvalidationService = sessionInvalidationService;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    // =============================
    // REQUEST RECOVERY EMAIL
    // =============================

    @Transactional
    public void requestRecovery(ForgotPasswordRequest request) {

        String identifier = request.identifier().strip();

        User existingUser = userRepository.findByUsername(identifier)
                .or(() -> userRepository.findByEmail(identifier))
                .orElse(null);

        if (existingUser == null) {
            return;
        }

        User user = userRepository
                .findByIdForUpdate(existingUser.getId())
                .orElse(null);

        if (user == null) {
            return;
        }

        // Reload after acquiring the account lock.
        entityManager.refresh(user);

        boolean identifierStillMatches =
                identifier.equalsIgnoreCase(user.getUsername())
                || identifier.equalsIgnoreCase(user.getEmail());

        if (!identifierStillMatches
                || user.getStatus() != UserStatus.ACTIVE
                || !user.isEmailVerified()) {
            return;
        }

        Instant now = Instant.now();

        boolean cooldownActive = tokenRepository
                .findFirstByUserIdOrderByCreatedAtDescIdDesc(user.getId())
                .map(token -> token.getCreatedAt()
                        .plus(RESEND_COOLDOWN)
                        .isAfter(now))
                .orElse(false);

        if (cooldownActive) {
            return;
        }

        invalidateUnusedTokens(user.getId(), now);

        String rawToken = secureTokenService.generateToken();
        String tokenHash = secureTokenService.hashToken(rawToken);

        PasswordResetToken token = new PasswordResetToken(
                user.getId(),
                tokenHash,
                now,
                now.plus(TOKEN_LIFETIME)
        );

        // Check database constraints before sending the email.
        tokenRepository.saveAndFlush(token);

        String resetLink = frontendUrl
                + "/reset-password#token="
                + rawToken;

        String body = """
                We received a request to recover your Cellbank account.

                Your username: %s

                To reset your password, open this link:

                %s

                This link expires in 30 minutes and can be used once.
                Requesting a new link invalidates previous reset links.

                If you only forgot your username, you can use the
                username above to sign in with your existing password.

                Your password will not change unless you complete
                the password reset.

                If you did not request this email, you can ignore it.
                """.formatted(user.getUsername(), resetLink);

        try {
            emailService.send(
                    user.getEmail(),
                    "Recover your Cellbank account",
                    body
            );
        } catch (MailException exception) {
            // Undo token changes if the mail service reports failure.
            TransactionAspectSupport.currentTransactionStatus()
                    .setRollbackOnly();

            // Do not log the recipient, raw token, or email body.
            log.warn("Account recovery email could not be sent.");
        }
    }

    // =============================
    // RESET PASSWORD
    // =============================

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {

        String rawToken = request.token();

        if (rawToken == null
                || !rawToken.matches("[A-Za-z0-9_-]{43}")) {
            throw invalidResetToken();
        }

        String tokenHash = secureTokenService.hashToken(rawToken);

        PasswordResetToken token = tokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(this::invalidResetToken);

        User user = userRepository
                .findByIdForUpdate(token.getUserId())
                .orElseThrow(this::invalidResetToken);

        // Reload both records after acquiring the account lock.
        entityManager.refresh(user);
        entityManager.refresh(token);

        Instant now = Instant.now();

        if (token.isUsed()
                || token.isExpired(now)
                || user.getStatus() != UserStatus.ACTIVE
                || !user.isEmailVerified()) {
            throw invalidResetToken();
        }

        String newPassword = request.newPassword();

        validateNewPassword(newPassword, user);

        user.setPasswordHash(passwordEncoder.encode(newPassword));

        invalidateUnusedTokens(user.getId(), now);

        userRepository.save(user);

        sessionInvalidationService.expireSessionsAfterCommit(
                user.getUsername()
        );
    }

    // =============================
    // PASSWORD VALIDATION
    // =============================

    private void validateNewPassword(String newPassword, User user) {

        if (newPassword == null
                || newPassword.isBlank()
                || newPassword.length() < 8
                || newPassword.length() > 72) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password must contain between 8 and 72 characters."
            );
        }

        if (newPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "New password must not exceed 72 UTF-8 bytes."
            );
        }

        if (passwordEncoder.matches(
                newPassword,
                user.getPasswordHash())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "New password must differ from your current password."
            );
        }
    }

    // =============================
    // TOKEN INVALIDATION
    // =============================

    private void invalidateUnusedTokens(Long userId, Instant now) {

        tokenRepository.findAllByUserIdAndUsedAtIsNull(userId)
                .forEach(token -> token.markUsed(now));
    }

    // =============================
    // INVALID TOKEN RESPONSE
    // =============================

    private ResponseStatusException invalidResetToken() {

        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "This reset link is invalid or expired. Request a new link."
        );
    }
}
