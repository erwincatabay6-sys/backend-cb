package com.cellbank.notification;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        Long repairJobId,
        String type,
        String message,
        boolean read,
        Instant createdAt,
        Instant readAt
) {
}