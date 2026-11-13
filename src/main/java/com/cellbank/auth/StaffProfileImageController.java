package com.cellbank.auth;

import java.security.Principal;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff")
public class StaffProfileImageController {

    private final ProfileImageService profileImageService;

    public StaffProfileImageController(
            ProfileImageService profileImageService) {

        this.profileImageService = profileImageService;
    }

    @GetMapping("/{staffId}/profile-image")
    public ResponseEntity<byte[]> getStaffImage(
            Principal principal,
            @PathVariable("staffId") Long staffId) {

        byte[] imageData = profileImageService.getStaffImage(
                principal.getName(),
                staffId
        );

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .contentLength(imageData.length)
                .cacheControl(CacheControl.noStore())
                .body(imageData);
    }
}
