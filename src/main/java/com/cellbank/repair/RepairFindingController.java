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
@RequestMapping("/api/repairs/{repairId}/findings")
public class RepairFindingController {

    private final RepairFindingService findingService;

    public RepairFindingController(
            RepairFindingService findingService) {

        this.findingService = findingService;
    }

    @GetMapping
    public List<RepairFindingResponse> getFindings(
            @PathVariable("repairId") Long repairId) {

        return findingService.getFindings(repairId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RepairFindingResponse createFinding(
            Principal principal,
            @PathVariable("repairId") Long repairId,
            @Valid @RequestBody RepairFindingCreateRequest request) {

        return findingService.createFinding(
                principal.getName(),
                repairId,
                request
        );
    }
}