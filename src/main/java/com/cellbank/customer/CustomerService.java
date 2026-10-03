package com.cellbank.customer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final DeviceRepository deviceRepository;

    public CustomerService(
            CustomerRepository customerRepository,
            DeviceRepository deviceRepository) {

        this.customerRepository = customerRepository;
        this.deviceRepository = deviceRepository;
    }

    public List<CustomerResponse> getCustomers() {
        Sort sorting = Sort.by(
                Sort.Order.asc("name").ignoreCase(),
                Sort.Order.asc("id")
        );

        List<Customer> customers =
                customerRepository.findAll(sorting);

        if (customers.isEmpty()) {
            return List.of();
        }

        List<Long> customerIds = customers.stream()
                .map(Customer::getId)
                .toList();

        List<Device> devices =
                deviceRepository.findByCustomerIdInOrderByIdAsc(
                        customerIds
                );

        Map<Long, List<DeviceResponse>> devicesByCustomer =
                new HashMap<>();

        for (Device device : devices) {
            devicesByCustomer
                    .computeIfAbsent(
                            device.getCustomerId(),
                            ignored -> new ArrayList<>()
                    )
                    .add(DeviceResponse.from(device));
        }

        return customers.stream()
                .map(customer -> CustomerResponse.from(
                        customer,
                        devicesByCustomer.getOrDefault(
                                customer.getId(),
                                List.of()
                        )
                ))
                .toList();
    }

    public CustomerResponse getCustomer(Long customerId) {
        Customer customer = requireCustomer(customerId);

        return CustomerResponse.from(
                customer,
                getCustomerDevices(customerId)
        );
    }

    @Transactional
    public CustomerResponse createCustomer(CustomerRequest request) {
        Customer customer = new Customer(
                request.name(),
                request.phone(),
                request.email(),
                request.address()
        );

        Customer saved =
                customerRepository.saveAndFlush(customer);

        return CustomerResponse.from(saved, List.of());
    }

    @Transactional
    public CustomerResponse updateCustomer(
            Long customerId,
            CustomerRequest request) {

        Customer customer = requireCustomer(customerId);

        customer.updateDetails(
                request.name(),
                request.phone(),
                request.email(),
                request.address()
        );

        customerRepository.flush();

        return CustomerResponse.from(
                customer,
                getCustomerDevices(customerId)
        );
    }

    private List<DeviceResponse> getCustomerDevices(Long customerId) {
        return deviceRepository
                .findByCustomerIdOrderByIdAsc(customerId)
                .stream()
                .map(DeviceResponse::from)
                .toList();
    }

    private Customer requireCustomer(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Customer not found."
                ));
    }
}