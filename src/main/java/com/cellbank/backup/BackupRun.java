package com.cellbank.backup;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "backup_runs", schema = "public")
public class BackupRun {

    public enum BackupType {
        DAILY,
        MANUAL
    }

    public enum Status {
        RUNNING,
        SUCCESS,
        FAILED
    }

    public enum StorageProvider {
        LOCAL,
        S3
    }

    public enum CleanupStatus {
        NOT_REQUIRED,
        PENDING,
        SUCCESS,
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "backup_type", nullable = false, length = 10,
            updatable = false)
    private BackupType backupType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private Status status;

    @Column(name = "requested_by", updatable = false)
    private Long requestedBy;

    @Column(name = "started_at", nullable = false, updatable = false,
            columnDefinition = "timestamp with time zone")
    private Instant startedAt;

    @Column(name = "completed_at",
            columnDefinition = "timestamp with time zone")
    private Instant completedAt;

    @Column(name = "expires_at",
            columnDefinition = "timestamp with time zone")
    private Instant expiresAt;

    @Column(name = "deleted_at",
            columnDefinition = "timestamp with time zone")
    private Instant deletedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_provider", nullable = false, length = 10,
            updatable = false)
    private StorageProvider storageProvider;

    @Column(name = "object_key", unique = true, length = 500)
    private String objectKey;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "sha256", length = 64)
    private String sha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "cleanup_status", nullable = false, length = 15)
    private CleanupStatus cleanupStatus;

    @Column(name = "status_message", length = 500)
    private String statusMessage;

    protected BackupRun() {
    }

    public BackupRun(
            BackupType backupType,
            StorageProvider storageProvider,
            Long requestedBy,
            Instant startedAt) {

        this.backupType = Objects.requireNonNull(backupType);
        this.storageProvider = Objects.requireNonNull(storageProvider);
        this.requestedBy = requestedBy;
        this.startedAt = Objects.requireNonNull(startedAt);
        this.status = Status.RUNNING;
        this.cleanupStatus = CleanupStatus.NOT_REQUIRED;
        this.statusMessage = "Backup started.";
    }

    public void updateProgress(String message) {
        requireRunning();
        this.statusMessage = validateMessage(message);
    }

    /**
     * Call only after the stored backup has passed verification.
     */
    public void markSuccessful(
            String objectKey,
            long fileSizeBytes,
            String sha256,
            Instant completedAt) {

        requireRunning();
        validateCompletionTime(completedAt);

        if (objectKey == null || objectKey.isBlank()
                || objectKey.length() > 500) {
            throw new IllegalArgumentException("Invalid backup object key.");
        }

        if (fileSizeBytes <= 0) {
            throw new IllegalArgumentException("Backup file must not be empty.");
        }

        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid backup checksum.");
        }

        this.objectKey = objectKey;
        this.fileSizeBytes = fileSizeBytes;
        this.sha256 = sha256;
        this.completedAt = completedAt;
        this.status = Status.SUCCESS;
        this.cleanupStatus = CleanupStatus.PENDING;
        this.statusMessage = "Backup verified. Checking retention.";

        this.expiresAt = backupType == BackupType.MANUAL
                ? completedAt.plus(Duration.ofDays(30))
                : null;
    }

    /**
     * Supply a safe user-facing message, never a raw exception message.
     */
    public void markFailed(String message, Instant completedAt) {
        requireRunning();
        validateCompletionTime(completedAt);

        this.statusMessage = validateMessage(message);
        this.completedAt = completedAt;
        this.status = Status.FAILED;
        this.cleanupStatus = CleanupStatus.NOT_REQUIRED;
    }

    public void markCleanupSuccessful() {
        requireSuccessful();

        this.cleanupStatus = CleanupStatus.SUCCESS;
        this.statusMessage = "Backup completed.";
    }

    public void markCleanupFailed() {
        requireSuccessful();

        this.cleanupStatus = CleanupStatus.FAILED;
        this.statusMessage =
                "Backup completed; old-backup cleanup failed.";
    }

    /**
     * Call only after removal from storage has been confirmed.
     */
    public void markDeleted(Instant deletedAt) {
        requireSuccessful();
        Objects.requireNonNull(deletedAt);

        if (deletedAt.isBefore(completedAt)) {
            throw new IllegalArgumentException(
                    "Deletion cannot precede backup completion."
            );
        }

        if (this.deletedAt == null) {
            this.deletedAt = deletedAt;
        }
    }

    private void requireRunning() {
        if (status != Status.RUNNING) {
            throw new IllegalStateException("Backup is no longer running.");
        }
    }

    private void requireSuccessful() {
        if (status != Status.SUCCESS) {
            throw new IllegalStateException(
                    "This operation requires a successful backup."
            );
        }
    }

    private void validateCompletionTime(Instant completedAt) {
        Objects.requireNonNull(completedAt);

        if (completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException(
                    "Completion cannot precede backup start."
            );
        }
    }

    private static String validateMessage(String message) {
        if (message == null || message.isBlank()
                || message.length() > 500) {
            throw new IllegalArgumentException(
                    "Backup message must contain 1 to 500 characters."
            );
        }

        return message;
    }

    public Long getId() {
        return id;
    }

    public BackupType getBackupType() {
        return backupType;
    }

    public Status getStatus() {
        return status;
    }

    public Long getRequestedBy() {
        return requestedBy;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public StorageProvider getStorageProvider() {
        return storageProvider;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public CleanupStatus getCleanupStatus() {
        return cleanupStatus;
    }

    public String getStatusMessage() {
        return statusMessage;
    }
}