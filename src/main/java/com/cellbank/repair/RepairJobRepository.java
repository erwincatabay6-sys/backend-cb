package com.cellbank.repair;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepairJobRepository extends JpaRepository<RepairJob, Long> {

    List<RepairJob> findByDeviceIdOrderByCreatedAtDescIdDesc(Long deviceId);

    @Query("""
            select r from RepairJob r
            where r.deviceId in (
                select d.id from Device d
                where d.customerId = :customerId
            )
            order by r.createdAt desc, r.id desc
            """)
    List<RepairJob> findCustomerRepairs(
            @Param("customerId") Long customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RepairJob r where r.id = :repairId")
    Optional<RepairJob> findByIdForUpdate(
            @Param("repairId") Long repairId);
}