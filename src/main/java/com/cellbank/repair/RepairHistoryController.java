package com.cellbank.repair;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.security.Principal;

@RestController
@RequestMapping("/api/customers")
public class RepairHistoryController {

    private final RepairHistoryService repairHistoryService;

    public RepairHistoryController(
            RepairHistoryService repairHistoryService) {

        this.repairHistoryService = repairHistoryService;
    }

    @GetMapping("/{customerId}/repairs")
    public List<RepairResponse> getCustomerHistory(
            Principal principal,
            @PathVariable("customerId") Long customerId) {

        return repairHistoryService.getCustomerHistory(
                principal.getName(),
                customerId
        );
    }

    @GetMapping("/{customerId}/devices/{deviceId}/repairs")
    public List<RepairResponse> getDeviceHistory(
            Principal principal,
            @PathVariable("customerId") Long customerId,
            @PathVariable("deviceId") Long deviceId) {

        return repairHistoryService.getDeviceHistory(
                principal.getName(),
                customerId,
                deviceId
        );
    }
}
