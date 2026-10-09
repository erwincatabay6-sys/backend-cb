package com.cellbank.repair;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RepairPartUsageRepository
        extends JpaRepository<RepairPartUsage, Long> {

    List<RepairPartUsage> findByRepairJobIdOrderByRecordedAtDescIdDesc(
            Long repairJobId);

    List<RepairPartUsage> findByRepairJobIdInOrderByRecordedAtDescIdDesc(
            Collection<Long> repairJobIds);
}
