package com.cellbank.notification;

import java.time.Instant;

import com.cellbank.auth.CurrentUserService;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('ADMIN', 'FRONT_DESK', 'TECHNICIAN')")
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final CurrentUserService currentUserService;

    public NotificationService(
            NotificationRepository notificationRepository,
            CurrentUserService currentUserService) {

        this.notificationRepository = notificationRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional(
            readOnly = true,
            isolation = Isolation.REPEATABLE_READ
    )
    public NotificationListResponse getNotifications(String username) {

        Long recipientUserId = requireRecipientId(username);

        var notifications = notificationRepository
                .findTop50ByRecipientUserIdOrderByCreatedAtDescIdDesc(
                        recipientUserId
                )
                .stream()
                .map(this::toResponse)
                .toList();

        long unreadCount = notificationRepository
                .countByRecipientUserIdAndReadFalse(recipientUserId);

        return new NotificationListResponse(
                notifications,
                unreadCount
        );
    }

    @Transactional
    public void markAsRead(String username, Long notificationId) {

        Long recipientUserId = requireRecipientId(username);

        int updated = notificationRepository.markAsRead(
                notificationId,
                recipientUserId,
                Instant.now()
        );

        if (updated == 0
                && !notificationRepository.existsByIdAndRecipientUserId(
                        notificationId,
                        recipientUserId
                )) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Notification not found."
            );
        }
    }

    @Transactional
    public void markAllAsRead(String username) {

        Long recipientUserId = requireRecipientId(username);

        notificationRepository.markAllAsRead(
                recipientUserId,
                Instant.now()
        );
    }

    private Long requireRecipientId(String username) {

        var currentUser = currentUserService.getCurrentUser(username);

        boolean allowed = currentUser.roles().stream()
                .anyMatch(role ->
                        role.equals("ADMIN")
                                || role.equals("FRONT_DESK")
                                || role.equals("TECHNICIAN")
                );

        if (!allowed) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not have permission to access notifications."
            );
        }

        return currentUser.id();
    }

    private NotificationResponse toResponse(Notification notification) {

        return new NotificationResponse(
                notification.getId(),
                notification.getRepairJobId(),
                notification.getType(),
                notification.getMessage(),
                notification.isRead(),
                notification.getCreatedAt(),
                notification.getReadAt()
        );
    }
}
