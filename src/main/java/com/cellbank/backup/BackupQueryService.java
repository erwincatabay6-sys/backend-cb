package com.cellbank.backup;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.cellbank.backup.BackupRun.CleanupStatus;
import com.cellbank.backup.BackupRun.Status;
import com.cellbank.backup.BackupStatusResponse.Activity;
import com.cellbank.backup.BackupStatusResponse.BackupPage;
import com.cellbank.backup.BackupStatusResponse.RunInfo;

@Service
@PreAuthorize("hasRole('ADMIN')")
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class BackupQueryService {

    private final BackupSettings settings;
    private final BackupRunRepository runRepository;
    private final BackupEventRepository eventRepository;
    private final BackupExecutionService execution;
    private final BackupStorage storage;

    public BackupQueryService(
            BackupSettings settings,
            BackupRunRepository runRepository,
            BackupEventRepository eventRepository,
            BackupExecutionService execution,
            BackupStorage storage) {

        this.settings = settings;
        this.runRepository = runRepository;
        this.eventRepository = eventRepository;
        this.execution = execution;
        this.storage = storage;
    }

    public BackupStatusResponse getStatus() {
        Instant now = Instant.now();

        BackupRun latest = runRepository
                .findFirstByOrderByStartedAtDescIdDesc()
                .orElse(null);

        BackupRun lastSuccessful = runRepository
                .findFirstByStatusAndDeletedAtIsNullOrderByCompletedAtDescIdDesc(
                        Status.SUCCESS)
                .orElse(null);

        boolean running = execution.isBusy()
                || (latest != null
                    && (latest.getStatus() == Status.RUNNING
                        || (latest.getStatus() == Status.SUCCESS
                            && latest.getCleanupStatus()
                                == CleanupStatus.PENDING)));

        Instant manualAvailableAt = latest == null
                ? now
                : latest.getStartedAt().plusSeconds(60);

        List<BackupEvent> recentEvents = new ArrayList<>(
                eventRepository.findTop100ByOrderByIdDesc()
        );

        Collections.reverse(recentEvents);

        List<Activity> events = recentEvents.stream()
                .map(event -> new Activity(
                        event.getId(),
                        event.getBackupRunId(),
                        event.getOccurredAt(),
                        event.getLevel(),
                        event.getMessage()
                ))
                .toList();

        return new BackupStatusResponse(
                now,
                settings.isEnabled(),
                settings.isAutomaticEnabled(),
                running,
                storage.getProvider(),
                "Daily at 2:00 AM (Asia/Manila)",
                nextScheduledAt(now),
                manualAvailableAt,
                BackupSettings.DAILY_RETENTION_COUNT,
                30,
                toInfo(latest),
                toInfo(lastSuccessful),
                getAvailableBackups(0),
                events
        );
    }

    public BackupPage getAvailableBackups(int page) {
        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be zero or greater."
            );
        }

        PageRequest request = PageRequest.of(
                page,
                25,
                Sort.by(
                        Sort.Order.desc("completedAt"),
                        Sort.Order.desc("id")
                )
        );

        Page<BackupRun> results = runRepository
                .findByStatusAndDeletedAtIsNull(
                        Status.SUCCESS,
                        request
                );

        return new BackupPage(
                results.getContent().stream()
                        .map(this::toInfo)
                        .toList(),
                results.getNumber(),
                results.getTotalPages(),
                results.getTotalElements()
        );
    }

    public DownloadInfo getDownload(Long backupId) {
        BackupRun run = runRepository.findById(backupId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Backup was not found."
                ));

        if (run.getDeletedAt() != null) {
            throw new ResponseStatusException(
                    HttpStatus.GONE,
                    "This backup has been removed by the retention policy."
            );
        }

        if (run.getStatus() != Status.SUCCESS) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This backup is not ready for download."
            );
        }

        if (run.getStorageProvider() != storage.getProvider()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This backup uses different storage. "
                            + "Please contact the maintainer."
            );
        }

        return new DownloadInfo(
                run.getObjectKey(),
                "cellbank-backup-" + run.getId() + ".dump",
                run.getFileSizeBytes(),
                run.getSha256()
        );
    }

    private RunInfo toInfo(BackupRun run) {
        if (run == null) {
            return null;
        }

        boolean downloadAllowed = run.getStatus() == Status.SUCCESS
                && run.getDeletedAt() == null
                && run.getStorageProvider() == storage.getProvider();

        return new RunInfo(
                run.getId(),
                run.getBackupType(),
                run.getStatus(),
                run.getCleanupStatus(),
                run.getStatusMessage(),
                run.getStartedAt(),
                run.getCompletedAt(),
                run.getExpiresAt(),
                run.getDeletedAt(),
                run.getStorageProvider(),
                run.getFileSizeBytes(),
                run.getSha256(),
                downloadAllowed
        );
    }

    private Instant nextScheduledAt(Instant now) {
        if (!settings.isAutomaticEnabled()) {
            return null;
        }

        ZoneId zone = ZoneId.of(BackupSettings.TIME_ZONE);
        ZonedDateTime localNow = now.atZone(zone);

        ZonedDateTime next = localNow.toLocalDate()
                .atTime(2, 0)
                .atZone(zone);

        if (!next.toInstant().isAfter(now)) {
            next = next.plusDays(1);
        }

        return next.toInstant();
    }

    public record DownloadInfo(
            String objectKey,
            String fileName,
            long sizeBytes,
            String sha256) {
    }
}