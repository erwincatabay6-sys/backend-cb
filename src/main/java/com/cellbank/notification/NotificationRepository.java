package com.cellbank.notification;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository
        extends JpaRepository<Notification, Long> {

    List<Notification>
            findTop50ByRecipientUserIdOrderByCreatedAtDescIdDesc(
                    Long recipientUserId);

    long countByRecipientUserIdAndReadFalse(Long recipientUserId);

    boolean existsByIdAndRecipientUserId(Long id, Long recipientUserId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification n
            set n.read = true, n.readAt = :readAt
            where n.id = :id
              and n.recipientUserId = :recipientUserId
              and n.read = false
            """)
    int markAsRead(
            @Param("id") Long id,
            @Param("recipientUserId") Long recipientUserId,
            @Param("readAt") Instant readAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification n
            set n.read = true, n.readAt = :readAt
            where n.recipientUserId = :recipientUserId
              and n.read = false
            """)
    int markAllAsRead(
            @Param("recipientUserId") Long recipientUserId,
            @Param("readAt") Instant readAt);
}