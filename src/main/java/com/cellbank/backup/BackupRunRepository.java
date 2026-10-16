package com.cellbank.backup;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cellbank.backup.BackupRun.BackupType;
import com.cellbank.backup.BackupRun.Status;

public interface BackupRunRepository
        extends JpaRepository<BackupRun, Long> {

    Optional<BackupRun> findFirstByOrderByStartedAtDescIdDesc();

    List<BackupRun> findTop50ByOrderByStartedAtDescIdDesc();

    List<BackupRun> findByStatusOrderByStartedAtAscIdAsc(
            Status status);

    Optional<BackupRun>
            findFirstByStatusAndDeletedAtIsNullOrderByCompletedAtDescIdDesc(
                    Status status);

    List<BackupRun>
            findByStatusAndDeletedAtIsNullOrderByCompletedAtDescIdDesc(
                    Status status);

    List<BackupRun>
            findByBackupTypeAndStatusAndDeletedAtIsNullOrderByCompletedAtDescIdDesc(
                    BackupType backupType,
                    Status status);
    
    boolean existsByBackupTypeAndStartedAtGreaterThanEqualAndStartedAtLessThan(
            BackupType backupType,
            java.time.Instant start,
            java.time.Instant end);
    
    org.springframework.data.domain.Page<BackupRun>
    findByStatusAndDeletedAtIsNull(
            Status status,
            org.springframework.data.domain.Pageable pageable);
}