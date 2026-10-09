package com.cellbank.ai;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import com.cellbank.repair.RepairStatus;

public record AiMessageResponse(
        Long id,
        Long repairJobId,
        AiMessageSenderType senderType,
        Long senderUserId,
        String senderName,
        String messageText,
        String suggestedDiagnosis,
        List<String> recommendedParts,
        RepairStatus suggestedStatus,
        Instant createdAt,
        List<AiMessageAttachmentResponse> attachments
) {

    public AiMessageResponse {
        recommendedParts = recommendedParts == null
                ? List.of()
                : List.copyOf(recommendedParts);

        attachments = attachments == null
                ? List.of()
                : List.copyOf(attachments);
    }

    public static AiMessageResponse from(
            AiMessage message,
            String senderName,
            List<AiMessageAttachmentResponse> attachments) {

        List<String> parts = Arrays.stream(message.getRecommendedParts())
                .filter(part -> part != null && !part.isBlank())
                .map(String::strip)
                .distinct()
                .toList();

        return new AiMessageResponse(
                message.getId(),
                message.getRepairJobId(),
                message.getSenderType(),
                message.getSenderUserId(),
                senderName,
                message.getMessageText(),
                message.getSuggestedDiagnosis(),
                parts,
                message.getSuggestedStatus(),
                message.getCreatedAt(),
                attachments
        );
    }
}
