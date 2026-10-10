package com.cellbank.tracking;

import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

import com.cellbank.customer.Device;
import com.cellbank.customer.DeviceRepository;
import com.cellbank.repair.RepairJob;
import com.cellbank.repair.RepairJobRepository;
import com.cellbank.repair.RepairStatus;
import com.cellbank.repair.RepairStatusHistory;
import com.cellbank.repair.RepairStatusHistoryRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(
        readOnly = true,
        isolation = Isolation.REPEATABLE_READ
)
public class TrackingService {

    // Existing repair creation generates a 43-character URL-safe token.
    private static final Pattern TRACKING_CODE_PATTERN =
            Pattern.compile("[A-Za-z0-9_-]{43}");

    private final RepairJobRepository repairRepository;
    private final DeviceRepository deviceRepository;
    private final RepairStatusHistoryRepository historyRepository;

    public TrackingService(
            RepairJobRepository repairRepository,
            DeviceRepository deviceRepository,
            RepairStatusHistoryRepository historyRepository) {

        this.repairRepository = repairRepository;
        this.deviceRepository = deviceRepository;
        this.historyRepository = historyRepository;
    }

    public TrackingResponse trackRepair(String trackingCode) {
        String code = trackingCode == null ? "" : trackingCode.trim();

        // Preserve letter case: tracking codes are case-sensitive.
        if (!TRACKING_CODE_PATTERN.matcher(code).matches()) {
            throw notFound();
        }

        RepairJob repair = repairRepository.findByTrackingCode(code)
                .orElseThrow(this::notFound);

        Device device = deviceRepository.findById(repair.getDeviceId())
                .orElseThrow(this::notFound);

        List<RepairStatusHistory> history = historyRepository
                .findByRepairJobIdOrderByChangedAtAscIdAsc(repair.getId());

        List<TrackingResponse.StatusEntry> entries = history.stream()
                .map(entry -> new TrackingResponse.StatusEntry(
                        entry.getNewStatus(),
                        entry.getChangedAt(),
                        describeStatus(entry.getNewStatus())
                ))
                .toList();

        Instant lastUpdated = history.isEmpty()
                ? repair.getCreatedAt()
                : history.get(history.size() - 1).getChangedAt();

        return new TrackingResponse(
                repair.getTrackingCode(),
                device.getBrand() + " " + device.getModel(),
                repair.getStatus(),
                repair.getCreatedAt(),
                lastUpdated,
                entries
        );
    }

    private String describeStatus(RepairStatus status) {
        return switch (status) {
            case RECEIVED ->
                    "Device received by Cellbank.";

            case AWAITING_APPROVAL ->
                    "The repair is awaiting customer approval. "
                            + "Please contact Cellbank for details.";

            case IN_PROGRESS ->
                    "Repair work is in progress.";

            case AWAITING_PARTS ->
                    "The repair is waiting for required parts.";

            case READY_FOR_RELEASE ->
                    "Your device is ready for collection. "
                            + "Please contact Cellbank to arrange pickup.";

            case COMPLETED ->
                    "This repair has been completed and closed.";

            case CANCELLED ->
                    "This repair has been cancelled. "
                            + "Please contact Cellbank for details.";
        };
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Repair not found. Check the tracking code and try again."
        );
    }
}