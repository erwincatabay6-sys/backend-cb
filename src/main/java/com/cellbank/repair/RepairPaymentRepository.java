package com.cellbank.repair;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepairPaymentRepository
        extends JpaRepository<RepairPayment, Long> {

    List<RepairPayment> findByRepairJobIdOrderByPaidAtDescIdDesc(
            Long repairJobId);

    @Query("""
            SELECT COALESCE(SUM(payment.amount), 0)
            FROM RepairPayment payment
            WHERE payment.repairJobId = :repairJobId
            """)
    BigDecimal sumAmountByRepairJobId(
            @Param("repairJobId") Long repairJobId);
}
