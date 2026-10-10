package com.cellbank.notification;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "notifications", schema = "public")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_user_id", nullable = false, updatable = false)
    private Long recipientUserId;

    @Column(name = "repair_job_id", updatable = false)
    private Long repairJobId;

    @Column(name = "type", nullable = false, length = 50, updatable = false)
    private String type;

    @Column(name = "message", nullable = false, length = 255, updatable = false)
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false,
            columnDefinition = "timestamp with time zone"
    )
    private Instant createdAt;

    @Column(
            name = "read_at",
            columnDefinition = "timestamp with time zone"
    )
    private Instant readAt;

    protected Notification() {
    }

    public Notification(
            Long recipientUserId,
            Long repairJobId,
            String type,
            String message) {

        this.recipientUserId = recipientUserId;
        this.repairJobId = repairJobId;
        this.type = type;
        this.message = message;
        this.read = false;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public void markAsRead() {
        if (!read) {
            read = true;
            readAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getRecipientUserId() {
        return recipientUserId;
    }

    public Long getRepairJobId() {
        return repairJobId;
    }

    public String getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public boolean isRead() {
        return read;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }
}