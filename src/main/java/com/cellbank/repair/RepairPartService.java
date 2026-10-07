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
@PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN', 'FRONT_DESK')")
public class RepairPartService {

    private final RepairPartUsageRepository partRepository;
    private final RepairJobRepository repairJobRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    @PersistenceContext
    private EntityManager entityManager;

    public RepairPartService(
            RepairPartUsageRepository partRepository,
            RepairJobRepository repairJobRepository,
            UserRepository userRepository,
            CurrentUserService currentUserService) {

        this.partRepository = partRepository;
        this.repairJobRepository = repairJobRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
    }

    public List<RepairPartResponse> getParts(Long repairId) {

        if (!repairJobRepository.existsById(repairId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Repair not found."
            );
        }

        List<RepairPartUsage> parts = partRepository
                .findByRepairJobIdOrderByRecordedAtDescIdDesc(repairId);

        if (parts.isEmpty()) {
            return List.of();
        }

        List<Long> staffIds = parts.stream()
                .map(RepairPartUsage::getRecordedById)
                .distinct()
                .toList();

        Map<Long, String> staffNames = new HashMap<>();

        for (User staff : userRepository.findAllById(staffIds)) {
            staffNames.put(staff.getId(), staff.getFullName());
        }

        return parts.stream()
                .map(part -> RepairPartResponse.from(
                        part,
                        staffNames.get(part.getRecordedById())
                ))
                .toList();
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
    public RepairPartResponse createPart(
            String username,
            Long repairId,
            RepairPartCreateRequest request) {

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
                    "You do not have permission to record parts."
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
                            + "Reload the page before recording a part."
            );
        }

        if (repair.getStatus() == RepairStatus.COMPLETED
                || repair.getStatus() == RepairStatus.CANCELLED) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Parts cannot be added to a completed "
                            + "or cancelled repair."
            );
        }

        RepairPartUsage part = new RepairPartUsage(
                repair.getId(),
                recordedById,
                request.partName(),
                request.quantity(),
                request.unitCost()
        );

        RepairPartUsage saved = partRepository.saveAndFlush(part);

        entityManager.refresh(saved);

        return RepairPartResponse.from(
                saved,
                actingUser.getFullName()
        );
    }
}
