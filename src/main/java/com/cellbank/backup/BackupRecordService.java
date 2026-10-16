package com.cellbank.backup;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.cellbank.backup.BackupEvent.Level;
import com.cellbank.backup.BackupRun.BackupType;
import com.cellbank.backup.BackupRun.Status;
import com.cellbank.backup.BackupRun.StorageProvider;
import com.cellbank.backup.BackupStorage.StoredBackup;

@Service
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class BackupRecordService {

    private final BackupRunRepository runRepository;
    private final BackupEventRepository eventRepository;

    public BackupRecordService(
            BackupRunRepository runRepository,
            BackupEventRepository eventRepository) {

        this.runRepository = runRepository;
        this.eventRepository = eventRepository;
    }

    public Long createRun(
            BackupType type,
            StorageProvider provider,
            Long requestedBy) {

        BackupRun run = runRepository.save(
                new BackupRun(
                        type,
                        provider,
                        requestedBy,
                        Instant.now()
                )
        );

        String message = type == BackupType.DAILY
                ? "Daily backup started."
                : "Manual backup started.";

        addEvent(run.getId(), Level.INFO, message);

        return run.getId();
    }

    public void updateProgress(Long runId, String message) {
        BackupRun run = requireRun(runId);

        if (run.getStatus() != Status.RUNNING) {
            return;
        }

        run.updateProgress(message);
        addEvent(runId, Level.INFO, message);
    }

    public void markSuccessful(Long runId, StoredBackup stored) {
        BackupRun run = requireRun(runId);

        run.markSuccessful(
                stored.objectKey(),
                stored.sizeBytes(),
                stored.sha256(),
                completionTime(run)
        );

        addEvent(
                runId,
                Level.INFO,
                "Stored backup verified. Checking retention."
        );
    }

    /**
     * Only pass application-defined messages, never exception.getMessage().
     */
    public void markFailed(Long runId, String safeMessage) {
        BackupRun run = requireRun(runId);

        // A later cleanup error must not downgrade a successful backup.
        if (run.getStatus() != Status.RUNNING) {
            return;
        }

        run.markFailed(safeMessage, completionTime(run));
        addEvent(runId, Level.ERROR, safeMessage);
    }

    public void addWarning(Long runId, String safeMessage) {
        requireRun(runId);
        addEvent(runId, Level.WARNING, safeMessage);
    }

    public void markCleanupCompleted(Long runId, boolean successful) {
        BackupRun run = requireRun(runId);

        if (successful) {
            run.markCleanupSuccessful();
            addEvent(runId, Level.INFO, "Backup completed.");
        } else {
            run.markCleanupFailed();

            addEvent(
                    runId,
                    Level.WARNING,
                    "Backup completed; old-backup cleanup failed."
            );
        }
    }

    /**
     * Call only after the storage deletion has succeeded.
     */
    public void markDeleted(
            Long backupId,
            Long currentRunId,
            String message) {

        BackupRun currentRun = requireRun(currentRunId);
        BackupRun oldBackup = requireRun(backupId);

        if (currentRun.getStatus() != Status.SUCCESS
                || backupId.equals(currentRunId)) {
            throw new IllegalStateException(
                    "Retention requires a different successful backup."
            );
        }

        if (oldBackup.getDeletedAt() != null) {
            return;
        }

        oldBackup.markDeleted(completionTime(oldBackup));
        addEvent(currentRunId, Level.INFO, message);
    }

    private BackupRun requireRun(Long id) {
        return runRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException(
                        "Backup record was not found."
                ));
    }

    private Instant completionTime(BackupRun run) {
        Instant now = Instant.now();
        Instant minimum = run.getCompletedAt() != null
                ? run.getCompletedAt()
                : run.getStartedAt();

        return now.isBefore(minimum) ? minimum : now;
    }

    private void addEvent(Long runId, Level level, String message) {
        eventRepository.save(
                new BackupEvent(
                        runId,
                        level,
                        message,
                        Instant.now()
                )
        );
    }
}
