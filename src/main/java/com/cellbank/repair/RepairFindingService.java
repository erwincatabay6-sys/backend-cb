package com.cellbank.repair;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.cellbank.auth.CurrentUserService;
import com.cellbank.auth.User;
import com.cellbank.auth.UserRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
public class RepairFindingService {

    private final RepairFindingRepository findingRepository;
    private final RepairJobRepository repairJobRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    @PersistenceContext
    private EntityManager entityManager;

    public RepairFindingService(
            RepairFindingRepository findingRepository,
            RepairJobRepository repairJobRepository,
            UserRepository userRepository,
            CurrentUserService currentUserService) {

        this.findingRepository = findingRepository;
        this.repairJobRepository = repairJobRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
    }

    public List<RepairFindingResponse> getFindings(Long repairId) {

        if (!repairJobRepository.existsById(repairId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Repair not found."
            );
        }

        List<RepairFinding> findings = findingRepository
                .findByRepairJobIdOrderByRecordedAtDescIdDesc(repairId);

        if (findings.isEmpty()) {
            return List.of();
        }

        List<Long> staffIds = findings.stream()
                .map(RepairFinding::getRecordedById)
                .distinct()
                .toList();

        Map<Long, String> staffNames = new HashMap<>();

        for (User staff : userRepository.findAllById(staffIds)) {
            staffNames.put(staff.getId(), staff.getFullName());
        }

        return findings.stream()
                .map(finding -> RepairFindingResponse.from(
                        finding,
                        staffNames.get(finding.getRecordedById())
                ))
                .toList();
    }

    @Transactional
    public RepairFindingResponse createFinding(
            String username,
            Long repairId,
            RepairFindingCreateRequest request) {

        Long recordedById = currentUserService
                .getCurrentUser(username)
                .id();

        User actingUser = userRepository.findById(recordedById)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Your account could not be found."
                ));

        boolean permittedByRole = actingUser.getRoles().stream()
                .anyMatch(role ->
                        "ADMIN".equals(role.getName())
                                || "TECHNICIAN".equals(role.getName()));

        if (!permittedByRole) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not have permission to record findings."
            );
        }

        RepairJob repair = repairJobRepository
                .findByIdForUpdate(repairId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Repair not found."
                ));

        entityManager.refresh(repair);

        if (!Objects.equals(
                repair.getUpdatedAt(),
                request.expectedUpdatedAt())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This repair changed since you opened it. "
                            + "Reload the page before recording a finding."
            );
        }

        if (repair.getStatus() == RepairStatus.COMPLETED
                || repair.getStatus() == RepairStatus.CANCELLED) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Findings cannot be added to a completed "
                            + "or cancelled repair."
            );
        }

        RepairFinding finding = new RepairFinding(
                repair.getId(),
                recordedById,
                request.finding(),
                request.diagnosis(),
                request.actionTaken()
        );

        RepairFinding saved = findingRepository.saveAndFlush(finding);

        entityManager.refresh(saved);

        return RepairFindingResponse.from(
                saved,
                actingUser.getFullName()
        );
    }
}
