package com.cellbank.repair;

import java.security.Principal;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repairs")
public class RepairController {

    private final RepairService repairService;

    public RepairController(RepairService repairService) {
        this.repairService = repairService;
    }

    @GetMapping
    public List<RepairResponse> getRepairs() {
        return repairService.getRepairs();
    }
    
    @GetMapping("/technicians")
    public List<TechnicianOptionResponse> getTechnicianOptions() {
        return repairService.getTechnicianOptions();
    }

    @GetMapping("/{repairId}")
    public RepairResponse getRepair(
            @PathVariable("repairId") Long repairId) {

        return repairService.getRepair(repairId);
    }

    @GetMapping("/{repairId}/status-history")
    public List<RepairStatusHistoryResponse> getStatusHistory(
            @PathVariable("repairId") Long repairId) {

        return repairService.getStatusHistory(repairId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RepairResponse createRepair(
            Principal principal,
            @Valid @RequestBody RepairCreateRequest request) {

        return repairService.createRepair(
                principal.getName(),
                request
        );
    }
    
    @PatchMapping("/{repairId}/assignment")
    public RepairResponse updateAssignment(
            Principal principal,
            @PathVariable("repairId") Long repairId,
            @Valid @RequestBody RepairAssignmentRequest request) {

        return repairService.updateAssignment(
                principal.getName(),
                repairId,
                request
        );
    }
    
    @PatchMapping("/{repairId}/status")
    public RepairResponse updateStatus(
            Principal principal,
            @PathVariable("repairId") Long repairId,
            @Valid @RequestBody RepairStatusUpdateRequest request) {

        return repairService.updateStatus(
                principal.getName(),
                repairId,
                request
        );
    }
    
    @PatchMapping("/{repairId}/estimate")
    public RepairResponse updateEstimate(
            Principal principal,
            @PathVariable("repairId") Long repairId,
            @Valid @RequestBody RepairEstimateUpdateRequest request) {

        return repairService.updateEstimate(
                principal.getName(),
                repairId,
                request
        );
    }

    @PatchMapping("/{repairId}/agreed-price")
    public RepairResponse updateAgreedPrice(
            Principal principal,
            @PathVariable("repairId") Long repairId,
            @Valid @RequestBody RepairAgreedPriceUpdateRequest request) {

        return repairService.updateAgreedPrice(
                principal.getName(),
                repairId,
                request
        );
    }
    
}