package com.cellbank.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.EntityManager;

@Service
public class PasswordChangeService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionInvalidationService sessionInvalidationService;
    private final PasswordResetTokenRepository tokenRepository;
    private final EntityManager entityManager;

    public PasswordChangeService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            SessionInvalidationService sessionInvalidationService,
            PasswordResetTokenRepository tokenRepository,
            EntityManager entityManager) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionInvalidationService = sessionInvalidationService;
        this.tokenRepository = tokenRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public void changePassword(
            String username,
            ChangePasswordRequest request) {

        if (username == null || username.isBlank()) {
            throw unauthorized();
        }

        User existingUser = userRepository.findByUsername(username)
                .orElseThrow(this::unauthorized);

        User user = userRepository
                .findByIdForUpdate(existingUser.getId())
                .orElseThrow(this::unauthorized);

        // Read the latest account data after acquiring the lock.
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

        String currentPassword = request.currentPassword();
        String newPassword = request.newPassword();

        if (currentPassword == null
                || currentPassword.getBytes(StandardCharsets.UTF_8).length > 72
                || !passwordEncoder.matches(
                        currentPassword,
                        user.getPasswordHash())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Current password is incorrect."
            );
        }

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

        user.setPasswordHash(passwordEncoder.encode(newPassword));

        Instant now = Instant.now();

        tokenRepository.findAllByUserIdAndUsedAtIsNull(user.getId())
                .forEach(token -> token.markUsed(now));

        userRepository.save(user);

        sessionInvalidationService.expireSessionsAfterCommit(
                user.getUsername()
        );
    }

    private ResponseStatusException unauthorized() {

        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Please sign in again."
        );
    }
}