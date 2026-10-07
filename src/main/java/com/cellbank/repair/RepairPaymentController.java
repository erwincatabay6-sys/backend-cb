package com.cellbank.repair;

import java.security.Principal;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repairs/{repairId}/payments")
public class RepairPaymentController {

    private final RepairPaymentService paymentService;

    public RepairPaymentController(RepairPaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    public List<RepairPaymentResponse> getPayments(
            @PathVariable("repairId") Long repairId) {

        return paymentService.getPayments(repairId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RepairPaymentResponse createPayment(
            Principal principal,
            @PathVariable("repairId") Long repairId,
            @Valid @RequestBody RepairPaymentCreateRequest request) {

        return paymentService.createPayment(
                principal.getName(),
                repairId,
                request
        );
    }
}
