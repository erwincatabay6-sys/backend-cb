package com.cellbank.notification;

import java.util.List;

public record NotificationListResponse(
        List<NotificationResponse> notifications,
        long unreadCount
) {
    public NotificationListResponse {
        notifications = List.copyOf(notifications);
    }
}
