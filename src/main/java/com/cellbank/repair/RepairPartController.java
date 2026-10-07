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
@RequestMapping("/api/repairs/{repairId}/parts")
public class RepairPartController {

    private final RepairPartService partService;

    public RepairPartController(RepairPartService partService) {
        this.partService = partService;
    }

    @GetMapping
    public List<RepairPartResponse> getParts(
            @PathVariable("repairId") Long repairId) {

        return partService.getParts(repairId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RepairPartResponse createPart(
            Principal principal,
            @PathVariable("repairId") Long repairId,
            @Valid @RequestBody RepairPartCreateRequest request) {

        return partService.createPart(
                principal.getName(),
                repairId,
                request
        );
    }
}