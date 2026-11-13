package com.cellbank.repair;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RepairStatusHistoryRepository
        extends JpaRepository<RepairStatusHistory, Long> {

    List<RepairStatusHistory> findByRepairJobIdOrderByChangedAtAscIdAsc(
            Long repairJobId);

    long countByChangedByIdAndNewStatusAndChangedAtGreaterThanEqualAndChangedAtLessThan(
            Long changedById,
            RepairStatus newStatus,
            Instant startInclusive,
            Instant endExclusive);
}