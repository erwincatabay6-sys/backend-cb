package com.cellbank.ai;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AiMessageAttachmentRepository
        extends JpaRepository<AiMessageAttachment, Long> {

    List<AttachmentSummary>
            findByAiMessageIdInOrderByAiMessageIdAscDisplayOrderAsc(
                    Collection<Long> aiMessageIds);

    List<AiMessageAttachment>
            findAllByAiMessageIdInOrderByAiMessageIdAscDisplayOrderAsc(
                    Collection<Long> aiMessageIds);

    Optional<AiMessageAttachment> findByIdAndAiMessageId(
            Long id,
            Long aiMessageId);

    interface AttachmentSummary {

        Long getId();

        Long getAiMessageId();

        String getOriginalFilename();

        String getContentType();

        Long getFileSize();

        Short getDisplayOrder();

        java.time.Instant getCreatedAt();
    }
}