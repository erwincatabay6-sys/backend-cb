package com.cellbank.ai;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.cellbank.ai.AiTroubleshootingContext.DeviceDetails;
import com.cellbank.ai.AiTroubleshootingContext.FindingDetails;
import com.cellbank.ai.AiTroubleshootingContext.PartDetails;
import com.cellbank.ai.AiTroubleshootingContext.PreviousRepairDetails;
import com.cellbank.ai.AiTroubleshootingContext.RepeatedProblemDetails;
import com.cellbank.auth.CurrentUserService;
import com.cellbank.auth.User;
import com.cellbank.auth.UserRepository;
import com.cellbank.customer.Device;
import com.cellbank.customer.DeviceRepository;
import com.cellbank.repair.RepairFinding;
import com.cellbank.repair.RepairFindingRepository;
import com.cellbank.repair.RepairJob;
import com.cellbank.repair.RepairJobRepository;
import com.cellbank.repair.RepairPartUsage;
import com.cellbank.repair.RepairPartUsageRepository;
import com.cellbank.repair.RepairProblemCategory;
import com.cellbank.repair.RepairStatus;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
public class AiTroubleshootingContextService {

    private final RepairJobRepository repairJobRepository;
    private final DeviceRepository deviceRepository;
    private final RepairFindingRepository findingRepository;
    private final RepairPartUsageRepository partUsageRepository;
    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;

    public AiTroubleshootingContextService(
            RepairJobRepository repairJobRepository,
            DeviceRepository deviceRepository,
            RepairFindingRepository findingRepository,
            RepairPartUsageRepository partUsageRepository,
            CurrentUserService currentUserService,
            UserRepository userRepository) {

        this.repairJobRepository = repairJobRepository;
        this.deviceRepository = deviceRepository;
        this.findingRepository = findingRepository;
        this.partUsageRepository = partUsageRepository;
        this.currentUserService = currentUserService;
        this.userRepository = userRepository;
    }

    public AiTroubleshootingContext buildContext(
            String username,
            Long repairId) {

        requireAuthorizedUser(username);

        if (repairId == null || repairId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A valid repair ID is required.");
        }

        RepairJob currentRepair = repairJobRepository
                .findById(repairId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Repair not found."));

        Device device = deviceRepository
                .findById(currentRepair.getDeviceId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "The device linked to this repair was not found."));

        List<RepairJob> eligibleDeviceRepairs = repairJobRepository
                .findByDeviceIdOrderByCreatedAtDescIdDesc(device.getId())
                .stream()
                .filter(repair ->
                        repair.getStatus() != RepairStatus.CANCELLED)
                .toList();

        List<RepairJob> previousRepairs = eligibleDeviceRepairs
                .stream()
                .filter(repair ->
                        !Objects.equals(
                                repair.getId(),
                                currentRepair.getId()))
                .toList();

        // Always include the current repair's records.
        // Cancelled repairs are excluded from the other repair visits.
        List<Long> contextRepairIds = Stream.concat(
                        Stream.of(currentRepair.getId()),
                        previousRepairs.stream().map(RepairJob::getId))
                .distinct()
                .toList();

        Map<Long, List<FindingDetails>> findingsByRepair =
                loadFindings(contextRepairIds);

        Map<Long, List<PartDetails>> partsByRepair =
                loadParts(contextRepairIds);

        List<PreviousRepairDetails> previousRepairDetails =
                previousRepairs.stream()
                        .map(repair -> new PreviousRepairDetails(
                                repair.getId(),
                                repair.getRepairReference(),
                                repair.getReportedProblem(),
                                repair.getProblemCategory(),
                                repair.getServiceType(),
                                repair.getStatus(),
                                repair.getCreatedAt(),
                                findingsByRepair.getOrDefault(
                                        repair.getId(),
                                        List.of()),
                                partsByRepair.getOrDefault(
                                        repair.getId(),
                                        List.of())))
                        .toList();

        return new AiTroubleshootingContext(
                currentRepair.getId(),
                currentRepair.getRepairReference(),
                new DeviceDetails(
                        device.getType(),
                        device.getBrand(),
                        device.getModel()),
                currentRepair.getReportedProblem(),
                currentRepair.getProblemCategory(),
                currentRepair.getServiceType(),
                currentRepair.getStatus(),
                currentRepair.getUpdatedAt(),
                findingsByRepair.getOrDefault(
                        currentRepair.getId(),
                        List.of()),
                partsByRepair.getOrDefault(
                        currentRepair.getId(),
                        List.of()),
                previousRepairDetails,
                buildRepeatedProblems(eligibleDeviceRepairs));
    }

    private void requireAuthorizedUser(String username) {
        Long userId = currentUserService
                .getCurrentUser(username)
                .id();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Your account could not be found."));

        boolean permitted = user.getRoles().stream()
                .anyMatch(role ->
                        "ADMIN".equals(role.getName())
                                || "TECHNICIAN".equals(role.getName()));

        if (!permitted) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not have permission to use AI troubleshooting.");
        }
    }

    private Map<Long, List<FindingDetails>> loadFindings(
            List<Long> repairIds) {

        if (repairIds.isEmpty()) {
            return Map.of();
        }

        return findingRepository
                .findByRepairJobIdInOrderByRecordedAtDescIdDesc(repairIds)
                .stream()
                .collect(Collectors.groupingBy(
                        RepairFinding::getRepairJobId,
                        Collectors.mapping(
                                finding -> new FindingDetails(
                                        finding.getDescription(),
                                        finding.getDiagnosis(),
                                        finding.getActionTaken(),
                                        finding.getRecordedAt()),
                                Collectors.toList())));
    }

    private Map<Long, List<PartDetails>> loadParts(
            List<Long> repairIds) {

        if (repairIds.isEmpty()) {
            return Map.of();
        }

        return partUsageRepository
                .findByRepairJobIdInOrderByRecordedAtDescIdDesc(repairIds)
                .stream()
                .collect(Collectors.groupingBy(
                        RepairPartUsage::getRepairJobId,
                        Collectors.mapping(
                                part -> new PartDetails(
                                        part.getPartName(),
                                        part.getQuantity(),
                                        part.getRecordedAt()),
                                Collectors.toList())));
    }

    private List<RepeatedProblemDetails> buildRepeatedProblems(
            List<RepairJob> eligibleDeviceRepairs) {

        Map<RepairProblemCategory, Integer> counts =
                new EnumMap<>(RepairProblemCategory.class);

        for (RepairJob repair : eligibleDeviceRepairs) {
            RepairProblemCategory category = repair.getProblemCategory();

            if (category == null
                    || category == RepairProblemCategory.OTHER) {
                continue;
            }

            counts.merge(category, 1, Integer::sum);
        }

        return counts.entrySet().stream()
                .filter(entry -> entry.getValue() >= 2)
                .map(entry -> new RepeatedProblemDetails(
                        entry.getKey(),
                        entry.getValue()))
                .sorted(
                        Comparator.comparingInt(
                                RepeatedProblemDetails::repairCount)
                                .reversed()
                                .thenComparing(detail ->
                                        detail.problemCategory().name()))
                .toList();
    }
}