package com.cellbank.backup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.server.ResponseStatusException;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class BackupScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(BackupScheduler.class);

    private final BackupExecutionService executionService;

    public BackupScheduler(BackupExecutionService executionService) {
        this.executionService = executionService;
    }

    @Scheduled(
            cron = BackupSettings.DAILY_CRON,
            zone = BackupSettings.TIME_ZONE
    )
    public void runDailyBackup() {
        try {
            Long backupId = executionService.requestDailyBackup();

            if (backupId != null) {
                log.info("Scheduled daily backup accepted: {}", backupId);
            }
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().value() == 409) {
                log.warn(
                        "Scheduled daily backup skipped because another "
                        + "backup is already running."
                );
            } else {
                log.error("Scheduled daily backup could not start.", ex);
            }
        } catch (RuntimeException ex) {
            log.error("Unexpected error starting scheduled daily backup.", ex);
        }
    }
}