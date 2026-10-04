package com.cellbank.repair;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
            @PathVariable("customerId") Long customerId) {

        return repairHistoryService.getCustomerHistory(customerId);
    }

    @GetMapping("/{customerId}/devices/{deviceId}/repairs")
    public List<RepairResponse> getDeviceHistory(
            @PathVariable("customerId") Long customerId,
            @PathVariable("deviceId") Long deviceId) {

        return repairHistoryService.getDeviceHistory(
                customerId,
                deviceId
        );
    }
}
