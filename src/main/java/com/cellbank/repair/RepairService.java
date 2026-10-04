package com.cellbank.repair;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
    
    @PersistenceContext
    private EntityManager entityManager;
    
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

        return repairs.stream()
                .map(repair -> {
                    Long customerId =
                            customerIdsByDevice.get(repair.getDeviceId());

                    if (customerId == null) {
                        throw new IllegalStateException(
                                "A repair references a missing device."
                        );
                    }

                    return RepairResponse.from(repair, customerId);
                })
                .toList();
    }

    public RepairResponse getRepair(Long repairId) {
        RepairJob repair = requireRepair(repairId);
        Device device = requireDevice(repair.getDeviceId());

        return RepairResponse.from(repair, device.getCustomerId());
    }

    public List<RepairStatusHistoryResponse> getStatusHistory(
            Long repairId) {

        requireRepair(repairId);

        return historyRepository
                .findByRepairJobIdOrderByChangedAtAscIdAsc(repairId)
                .stream()
                .map(RepairStatusHistoryResponse::from)
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

            return RepairResponse.from(
                    repair,
                    device.getCustomerId()
            );
        }

        repair.assignTechnician(request.assignedTechnicianId());

        repairJobRepository.flush();

        // Return the timestamp exactly as stored by PostgreSQL.
        entityManager.refresh(repair);

        return RepairResponse.from(
                repair,
                device.getCustomerId()
        );
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

        return RepairResponse.from(saved, device.getCustomerId());
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
