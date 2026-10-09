package com.cellbank.ai;

import java.time.Instant;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "ai_message_attachments", schema = "public")
public class AiMessageAttachment {

    private static final int MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ai_message_id", nullable = false, updatable = false)
    private Long aiMessageId;

    @Column(
            name = "original_filename",
            nullable = false,
            length = 255,
            updatable = false
    )
    private String originalFilename;

    @Column(
            name = "content_type",
            nullable = false,
            length = 50,
            updatable = false
    )
    private String contentType;

    @Column(name = "file_size", nullable = false, updatable = false)
    private Long fileSize;

    @Column(
            name = "image_data",
            nullable = false,
            columnDefinition = "bytea",
            updatable = false
    )
    private byte[] imageData;

    @Column(name = "display_order", nullable = false, updatable = false)
    private Short displayOrder;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false,
            columnDefinition = "timestamp with time zone"
    )
    private Instant createdAt;

    protected AiMessageAttachment() {
    }

    public AiMessageAttachment(
            Long aiMessageId,
            String originalFilename,
            String contentType,
            byte[] imageData,
            short displayOrder) {

        if (aiMessageId == null || aiMessageId <= 0) {
            throw new IllegalArgumentException(
                    "A valid AI message ID is required."
            );
        }

        if (originalFilename == null
                || originalFilename.isBlank()
                || originalFilename.length() > 255) {

            throw new IllegalArgumentException(
                    "An attachment filename of up to 255 characters is required."
            );
        }

        if (contentType == null
                || !ALLOWED_CONTENT_TYPES.contains(contentType)) {

            throw new IllegalArgumentException(
                    "Only JPEG, PNG, and WebP images are supported."
            );
        }

        if (imageData == null
                || imageData.length == 0
                || imageData.length > MAX_IMAGE_SIZE_BYTES) {

            throw new IllegalArgumentException(
                    "Each image must contain data and must not exceed 10 MB."
            );
        }

        if (displayOrder < 1 || displayOrder > 3) {
            throw new IllegalArgumentException(
                    "Attachment display order must be between 1 and 3."
            );
        }

        this.aiMessageId = aiMessageId;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.imageData = imageData.clone();
        this.fileSize = (long) imageData.length;
        this.displayOrder = displayOrder;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getAiMessageId() {
        return aiMessageId;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public byte[] getImageData() {
        return imageData.clone();
    }

    public Short getDisplayOrder() {
        return displayOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
