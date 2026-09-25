package com.cellbank.auth;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_profile_images", schema = "public")
public class UserProfileImage {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "image_data", nullable = false, columnDefinition = "bytea")
    private byte[] imageData;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserProfileImage() {
    }

    public UserProfileImage(
            Long userId,
            byte[] imageData,
            String contentType) {

        this.userId = userId;
        this.imageData = imageData;
        this.contentType = contentType;
    }

    public Long getUserId() {
        return userId;
    }

    public byte[] getImageData() {
        return imageData;
    }

    public String getContentType() {
        return contentType;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void replaceImage(byte[] imageData, String contentType) {
        this.imageData = imageData;
        this.contentType = contentType;
    }

    @PrePersist
    @PreUpdate
    private void updateTimestamp() {
        this.updatedAt = Instant.now();
    }
}
