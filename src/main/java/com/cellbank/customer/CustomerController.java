package com.cellbank.customer;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import java.security.Principal;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public List<CustomerResponse> getCustomers(Principal principal) {
        return customerService.getCustomers(principal.getName());
    }

    @GetMapping("/{customerId}")
    public CustomerResponse getCustomer(
            Principal principal,
            @PathVariable("customerId") Long customerId) {

        return customerService.getCustomer(
                principal.getName(),
                customerId
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse createCustomer(
            @Valid @RequestBody CustomerRequest request) {

        return customerService.createCustomer(request);
    }

    @PutMapping("/{customerId}")
    public CustomerResponse updateCustomer(
            @PathVariable("customerId") Long customerId,
            @Valid @RequestBody CustomerRequest request) {

        return customerService.updateCustomer(customerId, request);
    }
}
