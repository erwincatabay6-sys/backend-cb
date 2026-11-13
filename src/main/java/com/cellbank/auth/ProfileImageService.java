package com.cellbank.auth;

import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProfileImageService {

    private final UserRepository userRepository;
    private final UserProfileImageRepository imageRepository;
    private final ProfileImageProcessor imageProcessor;
    private final CurrentUserService currentUserService;
    private final EntityManager entityManager;

    public ProfileImageService(
            UserRepository userRepository,
            UserProfileImageRepository imageRepository,
            ProfileImageProcessor imageProcessor,
            CurrentUserService currentUserService,
            EntityManager entityManager) {

        this.userRepository = userRepository;
        this.imageRepository = imageRepository;
        this.imageProcessor = imageProcessor;
        this.currentUserService = currentUserService;
        this.entityManager = entityManager;
    }

    // -----------------------------
    // UPLOAD PROFILE IMAGE
    // -----------------------------

    @Transactional
    public CurrentUserResponse upload(
            String username,
            MultipartFile file) {

        User existingUser = findActiveUser(username);

        // Process the image before taking the account lock.
        byte[] imageData = imageProcessor.process(file);

        User user = userRepository
                .findByIdForUpdate(existingUser.getId())
                .orElseThrow(this::unauthorized);

        // Reload the latest account data after obtaining the lock.
        entityManager.refresh(user);

        if (!user.getUsername().equalsIgnoreCase(username)) {
            throw unauthorized();
        }

        requireActive(user);

        UserProfileImage image = imageRepository
                .findById(user.getId())
                .orElseGet(() -> new UserProfileImage(
                        user.getId(),
                        imageData,
                        "image/png"
                ));

        image.replaceImage(imageData, "image/png");
        imageRepository.save(image);

        String imageUrl = "/api/auth/me/profile-image?v="
                + UUID.randomUUID();

        user.setProfileImageUrl(imageUrl);
        userRepository.save(user);

        return currentUserService.getCurrentUser(user.getUsername());
    }

    // -----------------------------
    // READ PROFILE IMAGE
    // -----------------------------

    @Transactional(readOnly = true)
    public byte[] getImage(String username) {

        User user = findActiveUser(username);

        UserProfileImage image = imageRepository
                .findById(user.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No profile picture has been uploaded."
                ));

        return image.getImageData();
    }
    
    @Transactional(readOnly = true)
    public byte[] getStaffImage(String username, Long staffId) {

        User actingUser = findActiveUser(username);

        boolean isAdmin = actingUser.getRoles().stream()
                .anyMatch(role -> "ADMIN".equals(role.getName()));

        boolean isStaff = actingUser.getRoles().stream()
                .anyMatch(role ->
                        "TECHNICIAN".equals(role.getName())
                                || "FRONT_DESK".equals(role.getName()));

        if (!isAdmin && !isStaff) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not have permission to view staff photos."
            );
        }

        if (staffId == null || staffId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A valid staff ID is required."
            );
        }

        if (!isAdmin && !actingUser.getId().equals(staffId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not have access to this staff photo."
            );
        }

        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Staff member not found."
                ));

        boolean eligibleStaff = staff.getRoles().stream()
                .anyMatch(role ->
                        "TECHNICIAN".equals(role.getName())
                                || "FRONT_DESK".equals(role.getName()));

        if (staff.getStatus() != UserStatus.ACTIVE || !eligibleStaff) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Staff member not found."
            );
        }

        UserProfileImage image = imageRepository.findById(staffId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No profile picture has been uploaded."
                ));

        return image.getImageData();
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
}