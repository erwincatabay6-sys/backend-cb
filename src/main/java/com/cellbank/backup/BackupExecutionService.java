package com.cellbank.backup;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.cellbank.backup.BackupExecutionLock.Handle;
import com.cellbank.backup.BackupRun.BackupType;
import com.cellbank.backup.BackupRun.CleanupStatus;
import com.cellbank.backup.BackupRun.Status;
import com.cellbank.backup.PostgresBackupExporter.ExportResult;

@Service
public class BackupExecutionService {

    private static final Logger log =
            LoggerFactory.getLogger(BackupExecutionService.class);

    private final BackupSettings settings;
    private final BackupExecutionLock executionLock;
    private final BackupRunRepository runRepository;
    private final BackupRecordService records;
    private final PostgresBackupExporter exporter;
    private final BackupStorage storage;
    private final BackupRetentionService retention;

    private final AtomicBoolean busy = new AtomicBoolean(false);

    // No queued backup backlog: reject additional work while occupied.
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new SynchronousQueue<>(),
            task -> {
                Thread thread = new Thread(task, "cellbank-backup");
                thread.setDaemon(false);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy()
    );

    public BackupExecutionService(
            BackupSettings settings,
            BackupExecutionLock executionLock,
            BackupRunRepository runRepository,
            BackupRecordService records,
            PostgresBackupExporter exporter,
            BackupStorage storage,
            BackupRetentionService retention) {

        this.settings = settings;
        this.executionLock = executionLock;
        this.runRepository = runRepository;
        this.records = records;
        this.exporter = exporter;
        this.storage = storage;
        this.retention = retention;
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Long requestManualBackup(Long requestedBy) {
        if (requestedBy == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "An authenticated administrator is required."
            );
        }

        return start(BackupType.MANUAL, requestedBy);
    }

    /**
     * Returns null if automatic backups are disabled or today's
     * daily attempt already exists.
     */
    public Long requestDailyBackup() {
        if (!settings.isAutomaticEnabled()) {
            return null;
        }

        return start(BackupType.DAILY, null);
    }

    public boolean isBusy() {
        return busy.get();
    }

    private Long start(BackupType type, Long requestedBy) {
        if (!settings.isEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Backups are currently disabled."
            );
        }

        if (!busy.compareAndSet(false, true)) {
            throw alreadyRunning();
        }

        Handle handle = null;
        Long runId = null;
        boolean submitted = false;

        try {
            handle = executionLock.tryAcquire();

            if (handle == null) {
                throw alreadyRunning();
            }

            handle.verify();
            recoverAbandonedRuns(handle);

            if (type == BackupType.DAILY && dailyAttemptExists()) {
                return null;
            }

            if (type == BackupType.MANUAL) {
                enforceManualCooldown();
            }

            runId = records.createRun(
                    type,
                    storage.getProvider(),
                    requestedBy
            );

            Long acceptedRunId = runId;
            Handle acceptedHandle = handle;

            executor.execute(() -> executeBackup(
                    acceptedRunId,
                    type,
                    acceptedHandle
            ));

            submitted = true;
            return runId;

        } catch (SQLException exception) {
            log.warn("Could not acquire the backup execution lock.");

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Backup processing is temporarily unavailable."
            );

        } catch (RejectedExecutionException exception) {
            if (runId != null) {
                recordFailureSafely(
                        runId,
                        "The backup worker could not start. Please try again."
                );
            }

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "The backup worker is unavailable. Please try again."
            );

        } finally {
            if (!submitted) {
                if (handle != null) {
                    handle.close();
                }

                busy.set(false);
            }
        }
    }

    private void executeBackup(
            Long runId,
            BackupType type,
            Handle handle) {

        ExportResult exported = null;
        boolean successful = false;

        try {
            handle.verify();

            exported = exporter.export(message -> {
                handle.verify();
                records.updateProgress(runId, message);
            });

            handle.verify();

            records.updateProgress(
                    runId,
                    "Saving the backup and verifying the stored copy."
            );

            String objectKey = "cellbank-"
                    + type.name().toLowerCase(Locale.ROOT)
                    + "-"
                    + UUID.randomUUID()
                    + ".dump";

            BackupStorage.StoredBackup stored = storage.store(
                    objectKey,
                    exported.file(),
                    exported.sizeBytes(),
                    exported.sha256()
            );

            handle.verify();
            records.markSuccessful(runId, stored);
            successful = true;

            retention.applyAfterSuccess(runId, handle::verify);

        } catch (Exception exception) {
            log.error(
                    "Backup run {} encountered an error.",
                    runId,
                    exception
            );

            if (successful) {
                try {
                    records.markCleanupCompleted(runId, false);
                } catch (RuntimeException recordingException) {
                    log.error(
                            "Could not save cleanup status for backup {}.",
                            runId
                    );
                }
            } else {
                recordFailureSafely(
                        runId,
                        "Backup failed or was interrupted. "
                                + "Please contact the maintainer."
                );
            }

        } finally {
            try {
                if (exported != null) {
                    try {
                        exported.close();
                    } catch (IOException exception) {
                        log.warn(
                                "Temporary export cleanup failed for backup {}.",
                                runId
                        );

                        try {
                            records.addWarning(
                                    runId,
                                    "A temporary export file needs "
                                            + "maintainer-assisted cleanup."
                            );
                        } catch (RuntimeException recordingException) {
                            log.warn(
                                    "Could not save the temporary-file "
                                            + "cleanup warning."
                            );
                        }
                    }
                }
            } finally {
                handle.close();
                busy.set(false);
            }
        }
    }

    private boolean dailyAttemptExists() {
        ZoneId zone = ZoneId.of(BackupSettings.TIME_ZONE);
        LocalDate today = LocalDate.now(zone);

        Instant start = today.atStartOfDay(zone).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(zone).toInstant();

        return runRepository
                .existsByBackupTypeAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                        BackupType.DAILY,
                        start,
                        end
                );
    }

    private void enforceManualCooldown() {
        runRepository.findFirstByOrderByStartedAtDescIdDesc()
                .ifPresent(latest -> {
                    Instant availableAt =
                            latest.getStartedAt().plusSeconds(60);

                    if (Instant.now().isBefore(availableAt)) {
                        throw new ResponseStatusException(
                                HttpStatus.TOO_MANY_REQUESTS,
                                "Please wait at least 60 seconds "
                                        + "between backup requests."
                        );
                    }
                });
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        if (!settings.isEnabled()
                || !busy.compareAndSet(false, true)) {
            return;
        }

        try (Handle handle = executionLock.tryAcquire()) {
            if (handle != null) {
                recoverAbandonedRuns(handle);
            }
        } catch (SQLException | RuntimeException exception) {
            log.warn(
                    "Interrupted backup records could not be checked "
                            + "during startup."
            );
        } finally {
            busy.set(false);
        }
    }

    /**
     * Only run while holding the database lock. Another instance may
     * still be completing a backup during an application restart.
     */
    private void recoverAbandonedRuns(Handle handle) {
        for (BackupRun run : runRepository
                .findByStatusOrderByStartedAtAscIdAsc(Status.RUNNING)) {

            handle.verify();

            records.markFailed(
                    run.getId(),
                    "Backup was interrupted before completion. "
                            + "Existing successful backups were retained."
            );
        }

        for (BackupRun run : runRepository
                .findByStatusAndDeletedAtIsNullOrderByCompletedAtDescIdDesc(
                        Status.SUCCESS)) {

            if (run.getCleanupStatus() == CleanupStatus.PENDING) {
                handle.verify();
                records.markCleanupCompleted(run.getId(), false);
            }
        }
    }

    private void recordFailureSafely(Long runId, String message) {
        try {
            records.markFailed(runId, message);
        } catch (RuntimeException exception) {
            log.error(
                    "Could not save failure status for backup {}. "
                            + "A later recovery check will reconcile it.",
                    runId
            );
        }
    }

    private ResponseStatusException alreadyRunning() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Another backup is already running."
        );
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
