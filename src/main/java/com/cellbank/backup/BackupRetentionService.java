package com.cellbank.backup;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.cellbank.backup.BackupRun.BackupType;
import com.cellbank.backup.BackupRun.Status;

@Service
public class BackupRetentionService {

    private static final Logger log =
            LoggerFactory.getLogger(BackupRetentionService.class);

    private final BackupRunRepository runRepository;
    private final BackupRecordService records;
    private final BackupStorage storage;

    public BackupRetentionService(
            BackupRunRepository runRepository,
            BackupRecordService records,
            BackupStorage storage) {

        this.runRepository = runRepository;
        this.records = records;
        this.storage = storage;
    }

    /**
     * The worker must call this while holding the backup execution lock,
     * immediately after successfully storing and verifying the new backup.
     */
    public void applyAfterSuccess(
            Long currentRunId,
            Runnable verifyLock) {

        verifyLock.run();
        BackupRun current = runRepository.findById(currentRunId)
                .orElseThrow(() -> new IllegalStateException(
                        "The current backup record was not found."
                ));

        if (current.getStatus() != Status.SUCCESS
                || current.getDeletedAt() != null
                || current.getStorageProvider() != storage.getProvider()) {
            throw new IllegalStateException(
                    "Retention requires a successful backup "
                            + "in the active storage."
            );
        }

        List<BackupRun> retained = runRepository
                .findByStatusAndDeletedAtIsNullOrderByCompletedAtDescIdDesc(
                        Status.SUCCESS
                );

        if (retained.isEmpty()) {
            throw new IllegalStateException(
                    "No successful backup is available for retention."
            );
        }

        // Always protect the newest successful backup as well as this run.
        Long newestBackupId = retained.get(0).getId();

        Instant now = Instant.now();
        int dailyPosition = 0;
        boolean cleanupSuccessful = true;

        for (BackupRun backup : retained) {
            boolean shouldDelete;

            if (backup.getBackupType() == BackupType.DAILY) {
                dailyPosition++;

                shouldDelete = dailyPosition
                        > BackupSettings.DAILY_RETENTION_COUNT;
            } else {
                shouldDelete = backup.getExpiresAt() != null
                        && !backup.getExpiresAt().isAfter(now);
            }

            if (!shouldDelete
                    || backup.getId().equals(currentRunId)
                    || backup.getId().equals(newestBackupId)) {
                continue;
            }

            // Never send another provider's object to the active storage.
            if (backup.getStorageProvider() != storage.getProvider()) {
                cleanupSuccessful = false;

                records.addWarning(
                        currentRunId,
                        "An older backup uses different storage "
                                + "and needs maintainer-assisted cleanup."
                );

                continue;
            }

            verifyLock.run();

            try {
                storage.delete(backup.getObjectKey());

                String message =
                        backup.getBackupType() == BackupType.DAILY
                                ? "An older daily backup was removed."
                                : "An expired manual backup was removed.";

                records.markDeleted(
                        backup.getId(),
                        currentRunId,
                        message
                );

            } catch (IOException | RuntimeException exception) {
                cleanupSuccessful = false;

                log.warn(
                        "Retention failed for backup {}.",
                        backup.getId(),
                        exception
                );

                records.addWarning(
                        currentRunId,
                        "An older backup could not be fully cleaned up. "
                                + "Cleanup will be retried after a "
                                + "later successful backup."
                );
            }
        }

        records.markCleanupCompleted(
                currentRunId,
                cleanupSuccessful
        );
    }
}