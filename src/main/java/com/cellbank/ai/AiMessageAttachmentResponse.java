package com.cellbank.ai;

import java.time.Instant;

public record AiMessageAttachmentResponse(
        Long id,
        String originalFilename,
        String contentType,
        Long fileSize,
        Short displayOrder,
        Instant createdAt,
        String imageUrl
) {

    public static AiMessageAttachmentResponse from(
            Long repairJobId,
            AiMessageAttachmentRepository.AttachmentSummary attachment) {

        String imageUrl = "/api/repairs/"
                + repairJobId
                + "/ai/messages/"
                + attachment.getAiMessageId()
                + "/attachments/"
                + attachment.getId();

        return new AiMessageAttachmentResponse(
                attachment.getId(),
                attachment.getOriginalFilename(),
                attachment.getContentType(),
                attachment.getFileSize(),
                attachment.getDisplayOrder(),
                attachment.getCreatedAt(),
                imageUrl
        );
    }
}