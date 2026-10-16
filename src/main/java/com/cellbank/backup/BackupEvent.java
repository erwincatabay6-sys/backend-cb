package com.cellbank.backup;

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
@Table(name = "backup_events", schema = "public")
public class BackupEvent {

    public enum Level {
        INFO,
        WARNING,
        ERROR
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "backup_run_id", nullable = false, updatable = false)
    private Long backupRunId;

    @Column(name = "occurred_at", nullable = false, updatable = false,
            columnDefinition = "timestamp with time zone")
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false, length = 10,
            updatable = false)
    private Level level;

    @Column(name = "message", nullable = false, length = 500,
            updatable = false)
    private String message;

    protected BackupEvent() {
    }

    public BackupEvent(
            Long backupRunId,
            Level level,
            String message,
            Instant occurredAt) {

        if (message == null || message.isBlank()
                || message.length() > 500) {
            throw new IllegalArgumentException(
                    "Event message must contain 1 to 500 characters."
            );
        }

        this.backupRunId = Objects.requireNonNull(backupRunId);
        this.level = Objects.requireNonNull(level);
        this.message = message;
        this.occurredAt = Objects.requireNonNull(occurredAt);
    }

    public Long getId() {
        return id;
    }

    public Long getBackupRunId() {
        return backupRunId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Level getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }
}
