package com.cellbank.auth;

import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PasswordChangeService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionInvalidationService sessionInvalidationService;

    public PasswordChangeService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            SessionInvalidationService sessionInvalidationService) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionInvalidationService = sessionInvalidationService;
    }

    @Transactional
    public void changePassword(
            String username,
            ChangePasswordRequest request) {

        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Please sign in again."
            );
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Please sign in again."
                ));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "This account is inactive."
            );
        }

        String currentPassword = request.currentPassword();
        String newPassword = request.newPassword();

        if (currentPassword.getBytes(StandardCharsets.UTF_8).length > 72
                || !passwordEncoder.matches(
                        currentPassword,
                        user.getPasswordHash())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Current password is incorrect."
            );
        }

        if (newPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "New password must not exceed 72 UTF-8 bytes."
            );
        }

        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "New password must differ from your current password."
            );
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));

        userRepository.save(user);

        sessionInvalidationService.expireSessionsAfterCommit(
                user.getUsername()
        );
    }
}