package com.cellbank.ai;

import java.time.Instant;

import com.cellbank.repair.RepairStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ai_messages", schema = "public")
public class AiMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "repair_job_id", nullable = false, updatable = false)
    private Long repairJobId;

    @Column(name = "sender_user_id", updatable = false)
    private Long senderUserId;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "sender_type",
            nullable = false,
            length = 20,
            updatable = false
    )
    private AiMessageSenderType senderType;

    @Column(
            name = "message_text",
            columnDefinition = "text",
            updatable = false
    )
    private String messageText;

    @Column(
            name = "suggested_diagnosis",
            columnDefinition = "text",
            updatable = false
    )
    private String suggestedDiagnosis;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(
            name = "recommended_parts",
            columnDefinition = "text[]",
            updatable = false
    )
    private String[] recommendedParts;

    @Enumerated(EnumType.STRING)
    @Column(name = "suggested_status", length = 30, updatable = false)
    private RepairStatus suggestedStatus;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false,
            columnDefinition = "timestamp with time zone"
    )
    private Instant createdAt;

    protected AiMessage() {
    }

    public static AiMessage staffMessage(
            Long repairJobId,
            Long senderUserId,
            String messageText) {

        requireRepairId(repairJobId);

        if (senderUserId == null || senderUserId <= 0) {
            throw new IllegalArgumentException(
                    "A valid staff sender is required."
            );
        }

        AiMessage message = new AiMessage();
        message.repairJobId = repairJobId;
        message.senderUserId = senderUserId;
        message.senderType = AiMessageSenderType.STAFF;
        message.messageText = normalize(messageText);

        return message;
    }

    public static AiMessage aiMessage(
            Long repairJobId,
            String messageText,
            String suggestedDiagnosis,
            String[] recommendedParts,
            RepairStatus suggestedStatus) {

        requireRepairId(repairJobId);

        String normalizedText = normalize(messageText);

        if (normalizedText == null) {
            throw new IllegalArgumentException(
                    "An AI response must contain message text."
            );
        }

        AiMessage message = new AiMessage();
        message.repairJobId = repairJobId;
        message.senderUserId = null;
        message.senderType = AiMessageSenderType.AI;
        message.messageText = normalizedText;
        message.suggestedDiagnosis = normalize(suggestedDiagnosis);
        message.recommendedParts = recommendedParts == null
                ? new String[0]
                : recommendedParts.clone();
        message.suggestedStatus = suggestedStatus;

        return message;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    private static void requireRepairId(Long repairJobId) {
        if (repairJobId == null || repairJobId <= 0) {
            throw new IllegalArgumentException(
                    "A valid repair is required."
            );
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public Long getId() {
        return id;
    }

    public Long getRepairJobId() {
        return repairJobId;
    }

    public Long getSenderUserId() {
        return senderUserId;
    }

    public AiMessageSenderType getSenderType() {
        return senderType;
    }

    public String getMessageText() {
        return messageText;
    }

    public String getSuggestedDiagnosis() {
        return suggestedDiagnosis;
    }

    public String[] getRecommendedParts() {
        return recommendedParts == null
                ? new String[0]
                : recommendedParts.clone();
    }

    public RepairStatus getSuggestedStatus() {
        return suggestedStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
