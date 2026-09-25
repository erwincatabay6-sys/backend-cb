package com.cellbank.auth;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/auth/me/profile-image")
public class ProfileImageController {

    private final ProfileImageService profileImageService;

    public ProfileImageController(
            ProfileImageService profileImageService) {

        this.profileImageService = profileImageService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CurrentUserResponse upload(
            Authentication authentication,
            @RequestParam("file") MultipartFile file) {

        return profileImageService.upload(
                authentication.getName(),
                file
        );
    }

    @GetMapping
    public ResponseEntity<byte[]> getImage(
            Authentication authentication) {

        byte[] imageData = profileImageService.getImage(
                authentication.getName()
        );

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .contentLength(imageData.length)
                .cacheControl(CacheControl.noStore())
                .body(imageData);
    }
}