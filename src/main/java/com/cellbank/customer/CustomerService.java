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
import com.cellbank.auth.CurrentUserService;
import com.cellbank.auth.CurrentUserResponse;

@Service
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final DeviceRepository deviceRepository;
    private final CurrentUserService currentUserService;

    public CustomerService(
            CustomerRepository customerRepository,
            DeviceRepository deviceRepository,
            CurrentUserService currentUserService) {

        this.customerRepository = customerRepository;
        this.deviceRepository = deviceRepository;
        this.currentUserService = currentUserService;
    }

    public List<CustomerResponse> getCustomers(String username) {
        var currentUser = currentUserService.getCurrentUser(username);
        boolean broadAccess = hasBroadCustomerAccess(currentUser);

        List<Customer> customers;

        if (broadAccess) {
            customers = customerRepository.findAll(
                    Sort.by(
                            Sort.Order.asc("name").ignoreCase(),
                            Sort.Order.asc("id")
                    )
            );
        } else {
            customers = customerRepository
                    .findCustomersAssignedToTechnician(currentUser.id());
        }

        if (customers.isEmpty()) {
            return List.of();
        }

        List<Long> customerIds = customers.stream()
                .map(Customer::getId)
                .toList();

        List<Device> devices;

        if (broadAccess) {
            devices = deviceRepository
                    .findByCustomerIdInOrderByIdAsc(customerIds);
        } else {
            devices = deviceRepository
                    .findDevicesAssignedToTechnicianForCustomers(
                            customerIds,
                            currentUser.id()
                    );
        }

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

    public CustomerResponse getCustomer(
            String username,
            Long customerId) {

        var currentUser = currentUserService.getCurrentUser(username);
        boolean broadAccess = hasBroadCustomerAccess(currentUser);

        if (broadAccess) {
            Customer customer = requireCustomer(customerId);

            return CustomerResponse.from(
                    customer,
                    getCustomerDevices(customerId)
            );
        }

        List<Device> assignedDevices = deviceRepository
                .findCustomerDevicesAssignedToTechnician(
                        customerId,
                        currentUser.id()
                );

        if (assignedDevices.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Customer not found."
            );
        }

        Customer customer = requireCustomer(customerId);

        return CustomerResponse.from(
                customer,
                assignedDevices.stream()
                        .map(DeviceResponse::from)
                        .toList()
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
    
    private boolean hasBroadCustomerAccess(CurrentUserResponse user) {
        boolean broadAccess = user.roles().contains("ADMIN")
                || user.roles().contains("FRONT_DESK");

        if (!broadAccess && !user.roles().contains("TECHNICIAN")) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not have permission to view customers."
            );
        }

        return broadAccess;
    }
}