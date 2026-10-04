package com.cellbank.repair;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RepairStatusHistoryRepository
        extends JpaRepository<RepairStatusHistory, Long> {

    List<RepairStatusHistory> findByRepairJobIdOrderByChangedAtAscIdAsc(
            Long repairJobId);
}