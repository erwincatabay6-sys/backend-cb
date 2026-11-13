package com.cellbank.customer;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    List<Device> findByCustomerIdOrderByIdAsc(Long customerId);

    List<Device> findByCustomerIdInOrderByIdAsc(
            Collection<Long> customerIds);

    Optional<Device> findByIdAndCustomerId(
            Long deviceId,
            Long customerId);
    
    @Query("""
            select d from Device d
            where d.customerId = :customerId
              and d.id in (
                  select r.deviceId from RepairJob r
                  where r.assignedTechnicianId = :technicianId
              )
            order by d.id
            """)
    List<Device> findCustomerDevicesAssignedToTechnician(
            @Param("customerId") Long customerId,
            @Param("technicianId") Long technicianId);

    @Query("""
            select d from Device d
            where d.customerId in :customerIds
              and d.id in (
                  select r.deviceId from RepairJob r
                  where r.assignedTechnicianId = :technicianId
              )
            order by d.id
            """)
    List<Device> findDevicesAssignedToTechnicianForCustomers(
            @Param("customerIds") Collection<Long> customerIds,
            @Param("technicianId") Long technicianId);

    @Query("""
            select d from Device d
            where d.id = :deviceId
              and d.customerId = :customerId
              and d.id in (
                  select r.deviceId from RepairJob r
                  where r.assignedTechnicianId = :technicianId
              )
            """)
    Optional<Device> findAssignedDevice(
            @Param("customerId") Long customerId,
            @Param("deviceId") Long deviceId,
            @Param("technicianId") Long technicianId);
}