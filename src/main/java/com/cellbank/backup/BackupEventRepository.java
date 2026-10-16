package com.cellbank.backup;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BackupEventRepository
        extends JpaRepository<BackupEvent, Long> {

    List<BackupEvent> findTop100ByOrderByIdDesc();

    List<BackupEvent> findTop100ByBackupRunIdOrderByIdDesc(
            Long backupRunId);
}