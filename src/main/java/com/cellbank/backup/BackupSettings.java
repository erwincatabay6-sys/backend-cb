package com.cellbank.backup;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.cellbank.backup.BackupRun.StorageProvider;

@Component
public class BackupSettings {

    public static final String DAILY_CRON = "0 0 2 * * *";
    public static final String TIME_ZONE = "Asia/Manila";
    public static final int DAILY_RETENTION_COUNT = 7;

    private final boolean enabled;
    private final boolean automaticEnabled;
    private final StorageProvider storageProvider;
    private final Path localDirectory;
    private final Path workDirectory;
    private final String pgDumpExecutable;
    private final String pgRestoreExecutable;
    private final Duration processTimeout;

    public BackupSettings(
            @Value("${cellbank.backup.enabled:false}")
            boolean enabled,

            @Value("${cellbank.backup.automatic-enabled:false}")
            boolean automaticEnabled,

            @Value("${cellbank.backup.storage-provider:LOCAL}")
            StorageProvider storageProvider,

            @Value("${cellbank.backup.local-directory:"
                    + "${user.home}/cellbank-backups}")
            String localDirectory,

            @Value("${cellbank.backup.work-directory:"
                    + "${java.io.tmpdir}/cellbank-backup-work}")
            String workDirectory,

            @Value("${cellbank.backup.pg-dump-executable:pg_dump}")
            String pgDumpExecutable,

            @Value("${cellbank.backup.pg-restore-executable:pg_restore}")
            String pgRestoreExecutable,

            @Value("${cellbank.backup.process-timeout-seconds:1800}")
            long processTimeoutSeconds) {

        if (processTimeoutSeconds <= 0) {
            throw new IllegalArgumentException(
                    "Backup process timeout must be greater than zero."
            );
        }

        if (localDirectory == null || localDirectory.isBlank()
                || workDirectory == null || workDirectory.isBlank()) {
            throw new IllegalArgumentException(
                    "Backup directories must not be blank."
            );
        }

        if (pgDumpExecutable == null || pgDumpExecutable.isBlank()
                || pgRestoreExecutable == null
                || pgRestoreExecutable.isBlank()) {
            throw new IllegalArgumentException(
                    "PostgreSQL executable paths must not be blank."
            );
        }

        this.enabled = enabled;
        this.automaticEnabled = automaticEnabled;
        this.storageProvider = storageProvider;

        this.localDirectory = Path.of(localDirectory)
                .toAbsolutePath()
                .normalize();

        this.workDirectory = Path.of(workDirectory)
                .toAbsolutePath()
                .normalize();

        if (this.workDirectory.startsWith(this.localDirectory)
                || this.localDirectory.startsWith(this.workDirectory)) {
            throw new IllegalArgumentException(
                    "Backup storage and temporary work directories "
                            + "must be separate and must not contain each other."
            );
        }

        this.pgDumpExecutable = pgDumpExecutable.strip();
        this.pgRestoreExecutable = pgRestoreExecutable.strip();
        this.processTimeout = Duration.ofSeconds(processTimeoutSeconds);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isAutomaticEnabled() {
        return enabled && automaticEnabled;
    }

    public StorageProvider getStorageProvider() {
        return storageProvider;
    }

    public Path getLocalDirectory() {
        return localDirectory;
    }

    public Path getWorkDirectory() {
        return workDirectory;
    }

    public String getPgDumpExecutable() {
        return pgDumpExecutable;
    }

    public String getPgRestoreExecutable() {
        return pgRestoreExecutable;
    }

    public Duration getProcessTimeout() {
        return processTimeout;
    }
}