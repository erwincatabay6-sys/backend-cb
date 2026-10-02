package com.cellbank.auth;

import java.util.List;

import jakarta.persistence.EntityManager;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CurrentUserService {

    private final UserRepository userRepository;
    private final EntityManager entityManager;

    public CurrentUserService(
            UserRepository userRepository,
            EntityManager entityManager) {

        this.userRepository = userRepository;
        this.entityManager = entityManager;
    }

    // -----------------------------
    // CURRENT USER
    // -----------------------------

    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(String username) {

        User user = findActiveUser(username);

        return toResponse(user);
    }

    // -----------------------------
    // UPDATE PROFILE
    // -----------------------------

    @Transactional
    public CurrentUserResponse updateProfile(
            String username,
            UpdateProfileRequest request) {

        User existingUser = findActiveUser(username);

        User user = userRepository
                .findByIdForUpdate(existingUser.getId())
                .orElseThrow(this::unauthorized);

        // Reload the latest account data after obtaining the lock.
        entityManager.refresh(user);

        if (!user.getUsername().equalsIgnoreCase(username)) {
            throw unauthorized();
        }

        requireActive(user);

        user.setFullName(request.name().trim());

        userRepository.save(user);

        return toResponse(user);
    }

    // -----------------------------
    // ACCOUNT VALIDATION
    // -----------------------------

    private User findActiveUser(String username) {

        if (username == null || username.isBlank()) {
            throw unauthorized();
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(this::unauthorized);

        requireActive(user);

        return user;
    }

    private void requireActive(User user) {

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "This account is inactive."
            );
        }
    }

    private ResponseStatusException unauthorized() {

        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Please sign in again."
        );
    }

    // -----------------------------
    // RESPONSE MAPPING
    // -----------------------------

    private CurrentUserResponse toResponse(User user) {

        List<String> roleNames = user.getRoles()
                .stream()
                .map(Role::getName)
                .sorted()
                .toList();

        return new CurrentUserResponse(
                user.getId(),
                user.getFullName(),
                user.getUsername(),
                user.getEmail(),
                roleNames,
                user.getStatus(),
                user.isEmailVerified(),
                user.getProfileImageUrl()
        );
    }
}