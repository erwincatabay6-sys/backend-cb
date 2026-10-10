package com.cellbank.repair;

import java.time.Instant;
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
    
    List<RepairPayment>
    findByPaidAtGreaterThanEqualAndPaidAtLessThanOrderByPaidAtAscIdAsc(
            Instant startInclusive,
            Instant endExclusive);

    List<RepairPayment>
    findByPaidAtGreaterThanEqualOrderByPaidAtAscIdAsc(
            Instant startInclusive);

    List<RepairPayment>
    findByPaidAtLessThanOrderByPaidAtAscIdAsc(
            Instant endExclusive);

    @Query("""
    select payment.repairJobId as repairJobId,
           sum(payment.amount) as totalPaid
    from RepairPayment payment
    where payment.repairJobId in :repairIds
    group by payment.repairJobId
    """)
    List<RepairPaymentTotal> sumPaymentsByRepairIds(
    @Param("repairIds") List<Long> repairIds);

    interface RepairPaymentTotal {

    	Long getRepairJobId();

    	BigDecimal getTotalPaid();
}
}
