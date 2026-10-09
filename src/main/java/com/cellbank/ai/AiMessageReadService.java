package com.cellbank.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.cellbank.auth.CurrentUserService;
import com.cellbank.auth.User;
import com.cellbank.auth.UserRepository;
import com.cellbank.repair.RepairJobRepository;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
public class AiMessageReadService {

    private final AiMessageRepository messageRepository;
    private final AiMessageAttachmentRepository attachmentRepository;
    private final RepairJobRepository repairJobRepository;
    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;

    public AiMessageReadService(
            AiMessageRepository messageRepository,
            AiMessageAttachmentRepository attachmentRepository,
            RepairJobRepository repairJobRepository,
            CurrentUserService currentUserService,
            UserRepository userRepository) {

        this.messageRepository = messageRepository;
        this.attachmentRepository = attachmentRepository;
        this.repairJobRepository = repairJobRepository;
        this.currentUserService = currentUserService;
        this.userRepository = userRepository;
    }

    public List<AiMessageResponse> getMessages(
            String username,
            Long repairId) {

        requireRepairAccess(username, repairId);

        List<AiMessage> messages = messageRepository
                .findByRepairJobIdOrderByCreatedAtAscIdAsc(repairId);

        if (messages.isEmpty()) {
            return List.of();
        }

        List<Long> messageIds = messages.stream()
                .map(AiMessage::getId)
                .toList();

        List<Long> senderIds = messages.stream()
                .map(AiMessage::getSenderUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, String> senderNames = new HashMap<>();

        if (!senderIds.isEmpty()) {
            for (User sender : userRepository.findAllById(senderIds)) {
                senderNames.put(
                        sender.getId(),
                        sender.getFullName());
            }
        }

        Map<Long, List<AiMessageAttachmentResponse>> attachmentsByMessage =
                new HashMap<>();

        // Load attachment metadata only, not the stored image bytes.
        var attachments = attachmentRepository
                .findByAiMessageIdInOrderByAiMessageIdAscDisplayOrderAsc(
                        messageIds);

        for (var attachment : attachments) {
            attachmentsByMessage
                    .computeIfAbsent(
                            attachment.getAiMessageId(),
                            ignored -> new ArrayList<>())
                    .add(AiMessageAttachmentResponse.from(
                            repairId,
                            attachment));
        }

        return messages.stream()
                .map(message -> {
                    String senderName =
                            message.getSenderType() == AiMessageSenderType.AI
                                    ? "AI Assistant"
                                    : senderNames.getOrDefault(
                                            message.getSenderUserId(),
                                            "Staff");

                    return AiMessageResponse.from(
                            message,
                            senderName,
                            attachmentsByMessage.getOrDefault(
                                    message.getId(),
                                    List.of()));
                })
                .toList();
    }

    /**
     * Used internally by the image endpoint.
     * The controller will return the image bytes, not this entity as JSON.
     */
    public AiMessageAttachment getAttachment(
            String username,
            Long repairId,
            Long messageId,
            Long attachmentId) {

        requireRepairAccess(username, repairId);
        requirePositiveId(messageId, "message");
        requirePositiveId(attachmentId, "attachment");

        messageRepository.findByIdAndRepairJobId(messageId, repairId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "AI message not found for this repair."));

        return attachmentRepository
                .findByIdAndAiMessageId(attachmentId, messageId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "AI attachment not found for this message."));
    }

    private void requireRepairAccess(
            String username,
            Long repairId) {

        Long userId = currentUserService
                .getCurrentUser(username)
                .id();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Your account could not be found."));

        boolean permitted = user.getRoles().stream()
                .anyMatch(role ->
                        "ADMIN".equals(role.getName())
                                || "TECHNICIAN".equals(role.getName()));

        if (!permitted) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not have permission to view AI troubleshooting.");
        }

        requirePositiveId(repairId, "repair");

        if (!repairJobRepository.existsById(repairId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Repair not found.");
        }
    }

    private void requirePositiveId(Long id, String recordName) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A valid " + recordName + " ID is required.");
        }
    }
}