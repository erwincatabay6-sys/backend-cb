package com.cellbank.notification;

import java.util.LinkedHashSet;
import java.util.Set;

import com.cellbank.auth.UserRepository;
import com.cellbank.auth.UserStatus;
import com.cellbank.repair.RepairJob;
import com.cellbank.repair.RepairStatus;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class RepairNotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public RepairNotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository) {

        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    public void notifyAssignment(RepairJob repair) {

        Set<Long> recipients = new LinkedHashSet<>();
        addAssignedTechnician(recipients, repair);

        saveNotifications(
                recipients,
                repair,
                "REPAIR_ASSIGNED",
                "Repair " + repair.getRepairReference()
                        + " was assigned to you."
        );
    }

    public void notifyStatusChange(
            RepairJob repair,
            RepairStatus previousStatus,
            Long changedById) {

        if (previousStatus == repair.getStatus()) {
            return;
        }

        Set<Long> recipients = new LinkedHashSet<>();

        addActiveUsersWithRole(recipients, "ADMIN");
        addActiveUsersWithRole(recipients, "FRONT_DESK");
        addAssignedTechnician(recipients, repair);

        recipients.remove(changedById);

        saveNotifications(
                recipients,
                repair,
                "REPAIR_STATUS_CHANGED",
                "Repair " + repair.getRepairReference()
                        + " changed from " + statusLabel(previousStatus)
                        + " to " + statusLabel(repair.getStatus()) + "."
        );
    }

    private void addActiveUsersWithRole(
            Set<Long> recipients,
            String roleName) {

        userRepository
                .findDistinctByStatusAndRoles_NameOrderByFullNameAscIdAsc(
                        UserStatus.ACTIVE,
                        roleName
                )
                .forEach(user -> recipients.add(user.getId()));
    }

    private void addAssignedTechnician(
            Set<Long> recipients,
            RepairJob repair) {

        Long technicianId = repair.getAssignedTechnicianId();

        if (technicianId == null) {
            return;
        }

        userRepository
                .findByIdAndRoles_Name(technicianId, "TECHNICIAN")
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .ifPresent(user -> recipients.add(user.getId()));
    }

    private String statusLabel(RepairStatus status) {

        return switch (status) {
            case RECEIVED -> "Received";
            case AWAITING_APPROVAL -> "Awaiting approval";
            case IN_PROGRESS -> "In progress";
            case AWAITING_PARTS -> "Awaiting parts";
            case READY_FOR_RELEASE -> "Ready for release";
            case COMPLETED -> "Completed";
            case CANCELLED -> "Cancelled";
        };
    }

    private void saveNotifications(
            Set<Long> recipients,
            RepairJob repair,
            String type,
            String message) {

        for (Long recipientUserId : recipients) {
            notificationRepository.save(
                    new Notification(
                            recipientUserId,
                            repair.getId(),
                            type,
                            message
                    )
            );
        }
    }
}