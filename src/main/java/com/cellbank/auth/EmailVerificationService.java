package com.cellbank.auth;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.cellbank.email.EmailService;

import jakarta.persistence.EntityManager;

@Service
public class EmailVerificationService {

    private static final Duration TOKEN_LIFETIME = Duration.ofHours(24);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final SecureTokenService secureTokenService;
    private final EmailService emailService;
    private final EntityManager entityManager;
    private final String frontendUrl;

    public EmailVerificationService(
            UserRepository userRepository,
            EmailVerificationTokenRepository tokenRepository,
            SecureTokenService secureTokenService,
            EmailService emailService,
            EntityManager entityManager,
            @Value("${cellbank.frontend-url:http://localhost:5173}")
            String frontendUrl) {

        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.secureTokenService = secureTokenService;
        this.emailService = emailService;
        this.entityManager = entityManager;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }
    
    @Transactional(readOnly = true)
    public EmailVerificationStatusResponse getStatus(String username) {

        if (username == null || username.isBlank()) {
            throw unauthorized();
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(this::unauthorized);

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "This account is inactive."
            );
        }

        Instant now = Instant.now();
        Instant resendAvailableAt = null;

        if (!user.isEmailVerified()) {
            resendAvailableAt = tokenRepository
                    .findFirstByUserIdOrderByCreatedAtDescIdDesc(user.getId())
                    .map(token -> token.getCreatedAt().plus(RESEND_COOLDOWN))
                    .filter(nextAllowedTime -> nextAllowedTime.isAfter(now))
                    .orElse(null);
        }

        return new EmailVerificationStatusResponse(
                user.isEmailVerified(),
                now,
                resendAvailableAt
        );
    }
    
    @Transactional
    public void sendVerification(String username) {

        if (username == null || username.isBlank()) {
            throw unauthorized();
        }

        User existingUser = userRepository.findByUsername(username)
                .orElseThrow(this::unauthorized);

        User user = userRepository.findByIdForUpdate(existingUser.getId())
                .orElseThrow(this::unauthorized);

        // Reload after acquiring the lock in case another request
        // changed this account while we were waiting.
        entityManager.refresh(user);

        if (!user.getUsername().equalsIgnoreCase(username)) {
            throw unauthorized();
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "This account is inactive."
            );
        }

        if (user.isEmailVerified()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Your email is already verified."
            );
        }

        Instant now = Instant.now();

        tokenRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(
                user.getId()
        ).ifPresent(latestToken -> {
            Instant nextAllowedTime = latestToken.getCreatedAt()
                    .plus(RESEND_COOLDOWN);

            if (now.isBefore(nextAllowedTime)) {
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Please wait 60 seconds between verification requests."
                );
            }
        });

        invalidateUnusedTokens(user.getId(), now);

        String rawToken = secureTokenService.generateToken();
        String tokenHash = secureTokenService.hashToken(rawToken);

        EmailVerificationToken token = new EmailVerificationToken(
                user.getId(),
                tokenHash,
                user.getEmail(),
                now,
                now.plus(TOKEN_LIFETIME)
        );

        // Check database constraints before attempting email delivery.
        tokenRepository.saveAndFlush(token);

        String verificationLink = frontendUrl
                + "/verify-email#token="
                + rawToken;

        String body = """
                Verify your Cellbank email address by opening this link:

                %s

                This link expires in 24 hours and can be used once.
                Requesting a new link invalidates previous links.

                If you did not request this email, you can ignore it.
                """.formatted(verificationLink);

        try {
            emailService.send(
                    user.getEmail(),
                    "Verify your Cellbank email",
                    body
            );
        } catch (MailException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Unable to send the verification email. Please try again later."
            );
        }
    }

    @Transactional
    public void verifyEmail(String rawToken) {

        if (rawToken == null
                || !rawToken.matches("[A-Za-z0-9_-]{43}")) {
            throw invalidToken();
        }

        String tokenHash = secureTokenService.hashToken(rawToken);

        EmailVerificationToken token = tokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(this::invalidToken);

        User user = userRepository.findByIdForUpdate(token.getUserId())
                .orElseThrow(this::invalidToken);

        // The token was read before taking the user lock.
        // Reload both records so validation uses their latest values.
        entityManager.refresh(user);
        entityManager.refresh(token);

        Instant now = Instant.now();

        if (token.isUsed()
                || token.isExpired(now)
                || token.getEmail() == null
                || !token.getEmail().equalsIgnoreCase(user.getEmail())
                || user.getStatus() != UserStatus.ACTIVE) {

            throw invalidToken();
        }

        user.setEmailVerified(true);
        invalidateUnusedTokens(user.getId(), now);

        userRepository.save(user);
    }

    private void invalidateUnusedTokens(Long userId, Instant now) {

        tokenRepository.findAllByUserIdAndUsedAtIsNull(userId)
                .forEach(token -> token.markUsed(now));
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Please sign in again."
        );
    }

    private ResponseStatusException invalidToken() {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "This verification link is invalid or expired. Request a new link."
        );
    }
}
