package com.cellbank.auth;

import java.util.List;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(String username) {
        User user = findActiveUser(username);

        return toResponse(user);
    }

    @Transactional
    public CurrentUserResponse updateProfile(
            String username,
            UpdateProfileRequest request) {

        User user = findActiveUser(username);

        user.setFullName(request.name().trim());

        userRepository.save(user);

        return toResponse(user);
    }

    private User findActiveUser(String username) {

        if (username == null || username.isBlank()) {
            throw new BadCredentialsException("Please sign in again.");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new BadCredentialsException("Please sign in again."));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new DisabledException("This account is inactive.");
        }

        return user;
    }

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
