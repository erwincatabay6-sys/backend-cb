package com.cellbank.customer;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository
        extends JpaRepository<Customer, Long> {

    @Query("""
            select c from Customer c
            where c.id in (
                select d.customerId from Device d
                where d.id in (
                    select r.deviceId from RepairJob r
                    where r.assignedTechnicianId = :technicianId
                )
            )
            order by lower(c.name), c.id
            """)
    List<Customer> findCustomersAssignedToTechnician(
            @Param("technicianId") Long technicianId);
}
