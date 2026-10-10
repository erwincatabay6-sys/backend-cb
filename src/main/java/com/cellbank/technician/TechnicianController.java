package com.cellbank.technician;

import java.security.Principal;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/technicians")
public class TechnicianController {

    private final TechnicianService technicianService;

    public TechnicianController(TechnicianService technicianService) {
        this.technicianService = technicianService;
    }

    @GetMapping
    public List<TechnicianResponse> getTechnicians(Principal principal) {
        return technicianService.getTechnicians(principal.getName());
    }

    @GetMapping("/{technicianId}")
    public TechnicianDetailsResponse getTechnician(
            Principal principal,
            @PathVariable("technicianId") Long technicianId) {

        return technicianService.getTechnician(
                principal.getName(),
                technicianId
        );
    }
}
