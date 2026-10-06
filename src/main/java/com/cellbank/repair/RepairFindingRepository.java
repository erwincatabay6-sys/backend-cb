package com.cellbank.repair;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RepairFindingRepository
        extends JpaRepository<RepairFinding, Long> {

    List<RepairFinding> findByRepairJobIdOrderByRecordedAtDescIdDesc(
            Long repairJobId
    );
}