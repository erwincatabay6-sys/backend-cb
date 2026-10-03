package com.cellbank.customer;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    List<Device> findByCustomerIdOrderByIdAsc(Long customerId);

    List<Device> findByCustomerIdInOrderByIdAsc(
            Collection<Long> customerIds);

    Optional<Device> findByIdAndCustomerId(
            Long deviceId,
            Long customerId);
}