package com.cellbank.repair;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import com.cellbank.auth.CurrentUserService;
import com.cellbank.auth.User;
import com.cellbank.auth.UserRepository;
import com.cellbank.auth.UserStatus;
import com.cellbank.customer.Device;
import com.cellbank.customer.DeviceRepository;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('ADMIN', 'FRONT_DESK', 'TECHNICIAN')")
public class RepairService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RepairJobRepository repairJobRepository;
    private final RepairStatusHistoryRepository historyRepository;
    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    @PersistenceContext
    private EntityManager entityManager;

    public RepairService(
            RepairJobRepository repairJobRepository,
            RepairStatusHistoryRepository historyRepository,
            DeviceRepository deviceRepository,
            UserRepository userRepository,
            CurrentUserService currentUserService) {

        this.repairJobRepository = repairJobRepository;
        this.historyRepository = historyRepository;
        this.deviceRepository = deviceRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
    }

    public List<TechnicianOptionResponse> getTechnicianOptions() {
        return userRepository
                .findDistinctByStatusAndRoles_NameOrderByFullNameAscIdAsc(
                        UserStatus.ACTIVE,
                        "TECHNICIAN"
                )
                .stream()
                .map(TechnicianOptionResponse::from)
                .toList();
    }

    public List<RepairResponse> getRepairs() {
        List<RepairJob> repairs = repairJobRepository.findAll(
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );

        if (repairs.isEmpty()) {
            return List.of();
        }

        List<Long> deviceIds = repairs.stream()
                .map(RepairJob::getDeviceId)
                .distinct()
                .toList();

        Map<Long, Long> customerIdsByDevice = new HashMap<>();

        for (Device device : deviceRepository.findAllById(deviceIds)) {
            customerIdsByDevice.put(
                    device.getId(),
                    device.getCustomerId()
            );
        }

        Map<Long, String> staffNames = loadRepairStaffNames(repairs);

        return repairs.stream()
                .map(repair -> {
                    Long customerId =
                            customerIdsByDevice.get(repair.getDeviceId());

                    if (customerId == null) {
                        throw new IllegalStateException(
                                "A repair references a missing device."
                        );
                    }

                    return toResponse(repair, customerId, staffNames);
                })
                .toList();
    }

    public RepairResponse getRepair(Long repairId) {
        RepairJob repair = requireRepair(repairId);
        Device device = requireDevice(repair.getDeviceId());

        return toResponse(repair, device.getCustomerId());
    }

    public List<RepairStatusHistoryResponse> getStatusHistory(
            Long repairId) {

        requireRepair(repairId);

        List<RepairStatusHistory> history = historyRepository
                .findByRepairJobIdOrderByChangedAtAscIdAsc(repairId);

        Set<Long> staffIds = new HashSet<>();

        for (RepairStatusHistory entry : history) {
            staffIds.add(entry.getChangedById());
        }

        Map<Long, String> staffNames = loadStaffNames(staffIds);

        return history.stream()
                .map(entry -> RepairStatusHistoryResponse.from(
                        entry,
                        staffNames.get(entry.getChangedById())
                ))
                .toList();
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'FRONT_DESK')")
    public RepairResponse updateAssignment(
            String username,
            Long repairId,
            RepairAssignmentRequest request) {

        currentUserService.getCurrentUser(username);

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
                    "This repair was changed by another action. "
                            + "Reload the page before changing its assignment."
            );
        }

        if (repair.getStatus() == RepairStatus.COMPLETED
                || repair.getStatus() == RepairStatus.CANCELLED) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Technician assignment cannot be changed "
                            + "for a completed or cancelled repair."
            );
        }

        validateTechnician(request.assignedTechnicianId());

        Device device = requireDevice(repair.getDeviceId());

        if (Objects.equals(
                repair.getAssignedTechnicianId(),
                request.assignedTechnicianId())) {

            return toResponse(repair, device.getCustomerId());
        }

        repair.assignTechnician(request.assignedTechnicianId());

        repairJobRepository.flush();
        entityManager.refresh(repair);

        return toResponse(repair, device.getCustomerId());
    }
    
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'FRONT_DESK', 'TECHNICIAN')")
    public RepairResponse updateStatus(
            String username,
            Long repairId,
            RepairStatusUpdateRequest request) {

        Long changedById = currentUserService.getCurrentUser(username).id();

        var actingUser = userRepository.findById(changedById)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Your account could not be found."));

        RepairJob repair = repairJobRepository.findByIdForUpdate(repairId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Repair not found."));

        entityManager.refresh(repair);

        if (!Objects.equals(repair.getUpdatedAt(), request.expectedUpdatedAt())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This repair changed since you opened it. Reload the page before changing its status.");
        }

        RepairStatus previousStatus = repair.getStatus();
        RepairStatus newStatus = request.status();

        if (newStatus == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Select a repair status.");
        }

        boolean permittedByRole = actingUser.getRoles().stream()
                .anyMatch(role -> {
                    String roleName = role.getName();

                    if ("ADMIN".equals(roleName)) {
                        return true;
                    }

                    return switch (newStatus) {
                        case RECEIVED, COMPLETED, CANCELLED ->
                                "FRONT_DESK".equals(roleName);

                        case AWAITING_APPROVAL ->
                                "FRONT_DESK".equals(roleName)
                                        || "TECHNICIAN".equals(roleName);

                        case IN_PROGRESS, AWAITING_PARTS, READY_FOR_RELEASE ->
                                "TECHNICIAN".equals(roleName);
                    };
                });

        if (!permittedByRole) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not have permission to select this repair status.");
        }

        boolean allowedTransition = switch (previousStatus) {
            case RECEIVED ->
                    newStatus == RepairStatus.AWAITING_APPROVAL
                            || newStatus == RepairStatus.IN_PROGRESS
                            || newStatus == RepairStatus.CANCELLED;

            case AWAITING_APPROVAL ->
                    newStatus == RepairStatus.IN_PROGRESS
                            || newStatus == RepairStatus.CANCELLED;

            case IN_PROGRESS ->
                    newStatus == RepairStatus.AWAITING_APPROVAL
                            || newStatus == RepairStatus.AWAITING_PARTS
                            || newStatus == RepairStatus.READY_FOR_RELEASE
                            || newStatus == RepairStatus.CANCELLED;

            case AWAITING_PARTS ->
                    newStatus == RepairStatus.IN_PROGRESS
                            || newStatus == RepairStatus.READY_FOR_RELEASE
                            || newStatus == RepairStatus.CANCELLED;

            case READY_FOR_RELEASE ->
                    newStatus == RepairStatus.IN_PROGRESS
                            || newStatus == RepairStatus.COMPLETED;

            case COMPLETED, CANCELLED -> false;
        };

        if (!allowedTransition) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This status change is not allowed from the repair's current status.");
        }

        var device = requireDevice(repair.getDeviceId());

        repair.changeStatus(newStatus);

        historyRepository.saveAndFlush(
                new RepairStatusHistory(
                        repair.getId(),
                        changedById,
                        previousStatus,
                        newStatus,
                        request.note()));

        repairJobRepository.flush();
        entityManager.refresh(repair);

        return toResponse(repair, device.getCustomerId());
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'FRONT_DESK')")
    public RepairResponse createRepair(
            String username,
            RepairCreateRequest request) {

        Long creatorId =
                currentUserService.getCurrentUser(username).id();

        Device device = deviceRepository
                .findByIdAndCustomerId(
                        request.deviceId(),
                        request.customerId()
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Device not found for this customer."
                ));

        validateTechnician(request.assignedTechnicianId());

        RepairJob repair = new RepairJob(
                "TMP-" + randomToken(18),
                randomToken(32),
                device.getId(),
                request.assignedTechnicianId(),
                creatorId,
                request.reportedProblem(),
                request.serviceType(),
                request.accessoriesReceived(),
                request.intakeNotes(),
                RepairPriority.NORMAL,
                request.estimatedCost(),
                null,
                null
        );

        RepairJob saved = repairJobRepository.saveAndFlush(repair);

        saved.assignReferenceFromId();
        repairJobRepository.flush();

        RepairStatusHistory initialHistory = new RepairStatusHistory(
                saved.getId(),
                creatorId,
                null,
                RepairStatus.RECEIVED,
                "Repair created."
        );

        historyRepository.saveAndFlush(initialHistory);
        entityManager.refresh(saved);

        return toResponse(saved, device.getCustomerId());
    }

    private RepairResponse toResponse(
            RepairJob repair,
            Long customerId) {

        return toResponse(
                repair,
                customerId,
                loadRepairStaffNames(List.of(repair))
        );
    }

    private RepairResponse toResponse(
            RepairJob repair,
            Long customerId,
            Map<Long, String> staffNames) {

        return RepairResponse.from(
                repair,
                customerId,
                staffNames.get(repair.getAssignedTechnicianId()),
                staffNames.get(repair.getCreatedById())
        );
    }

    private Map<Long, String> loadRepairStaffNames(
            List<RepairJob> repairs) {

        Set<Long> staffIds = new HashSet<>();

        for (RepairJob repair : repairs) {
            staffIds.add(repair.getCreatedById());

            if (repair.getAssignedTechnicianId() != null) {
                staffIds.add(repair.getAssignedTechnicianId());
            }
        }

        return loadStaffNames(staffIds);
    }

    private Map<Long, String> loadStaffNames(Set<Long> staffIds) {
        Map<Long, String> names = new HashMap<>();

        if (!staffIds.isEmpty()) {
            for (User staff : userRepository.findAllById(staffIds)) {
                names.put(staff.getId(), staff.getFullName());
            }
        }

        return names;
    }

    private void validateTechnician(Long technicianId) {
        if (technicianId == null) {
            return;
        }

        User technician = userRepository.findById(technicianId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Selected technician does not exist."
                ));

        boolean hasTechnicianRole = technician.getRoles()
                .stream()
                .anyMatch(role -> "TECHNICIAN".equals(role.getName()));

        if (technician.getStatus() != UserStatus.ACTIVE
                || !hasTechnicianRole) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Select an active account with the technician role."
            );
        }
    }

    private RepairJob requireRepair(Long repairId) {
        return repairJobRepository.findById(repairId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Repair not found."
                ));
    }

    private Device requireDevice(Long deviceId) {
        return deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Device not found."
                ));
    }

    private static String randomToken(int byteCount) {
        byte[] bytes = new byte[byteCount];
        RANDOM.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }
}