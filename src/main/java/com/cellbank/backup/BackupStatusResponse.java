package com.cellbank.backup;

import java.time.Instant;
import java.util.List;

import com.cellbank.backup.BackupEvent.Level;
import com.cellbank.backup.BackupRun.BackupType;
import com.cellbank.backup.BackupRun.CleanupStatus;
import com.cellbank.backup.BackupRun.Status;
import com.cellbank.backup.BackupRun.StorageProvider;

public record BackupStatusResponse(
        Instant serverTime,
        boolean enabled,
        boolean automaticEnabled,
        boolean running,
        StorageProvider storageProvider,
        String schedule,
        Instant nextScheduledAt,
        Instant manualAvailableAt,
        int dailyRetentionCount,
        int manualRetentionDays,
        RunInfo latestAttempt,
        RunInfo lastSuccessful,
        BackupPage availableBackups,
        List<Activity> events) {

    public record RunInfo(
            Long id,
            BackupType backupType,
            Status status,
            CleanupStatus cleanupStatus,
            String statusMessage,
            Instant startedAt,
            Instant completedAt,
            Instant expiresAt,
            Instant deletedAt,
            StorageProvider storageProvider,
            Long fileSizeBytes,
            String sha256,
            boolean downloadAllowed) {
    }

    public record BackupPage(
            List<RunInfo> backups,
            int page,
            int totalPages,
            long totalElements) {
    }

    public record Activity(
            Long id,
            Long backupRunId,
            Instant occurredAt,
            Level level,
            String message) {
    }
}