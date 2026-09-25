package com.cellbank.auth;

import java.util.UUID;

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

    public ProfileImageService(
            UserRepository userRepository,
            UserProfileImageRepository imageRepository,
            ProfileImageProcessor imageProcessor,
            CurrentUserService currentUserService) {

        this.userRepository = userRepository;
        this.imageRepository = imageRepository;
        this.imageProcessor = imageProcessor;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public CurrentUserResponse upload(
            String username,
            MultipartFile file) {

        User user = findActiveUser(username);

        byte[] imageData = imageProcessor.process(file);

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

    private User findActiveUser(String username) {

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

        return user;
    }
}