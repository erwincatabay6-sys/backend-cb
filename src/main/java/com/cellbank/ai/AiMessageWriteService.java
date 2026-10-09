package com.cellbank.ai;

import java.util.List;
import java.util.Objects;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.cellbank.ai.AiMessageAttachmentValidator.ValidatedAttachment;
import com.cellbank.auth.CurrentUserService;
import com.cellbank.auth.User;
import com.cellbank.auth.UserRepository;
import com.cellbank.repair.RepairJob;
import com.cellbank.repair.RepairJobRepository;
import com.cellbank.repair.RepairStatus;

@Service
@PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
public class AiMessageWriteService {

    private final AiMessageRepository messageRepository;
    private final AiMessageAttachmentRepository attachmentRepository;
    private final RepairJobRepository repairJobRepository;
    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;
    private final AiMessageReadService readService;

    @PersistenceContext
    private EntityManager entityManager;

    public AiMessageWriteService(
            AiMessageRepository messageRepository,
            AiMessageAttachmentRepository attachmentRepository,
            RepairJobRepository repairJobRepository,
            CurrentUserService currentUserService,
            UserRepository userRepository,
            AiMessageReadService readService) {

        this.messageRepository = messageRepository;
        this.attachmentRepository = attachmentRepository;
        this.repairJobRepository = repairJobRepository;
        this.currentUserService = currentUserService;
        this.userRepository = userRepository;
        this.readService = readService;
    }

    /**
     * Called after the provider returns a complete, validated reply.
     * No OpenAI network call takes place inside this transaction.
     *
     * expectedLastMessageId comes from the backend's conversation snapshot.
     * It is null when that snapshot contained no saved messages.
     */
    @Transactional
    public List<AiMessageResponse> saveExchange(
            String username,
            Long repairId,
            AiMessageCreateRequest request,
            Long expectedLastMessageId,
            List<ValidatedAttachment> attachments,
            AiAssistantReply reply) {

        Long senderUserId = requireAuthorizedUser(username);

        if (repairId == null || repairId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A valid repair ID is required.");
        }

        if (request == null || request.expectedUpdatedAt() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Reload the repair before sending a message.");
        }

        if (expectedLastMessageId != null
                && expectedLastMessageId <= 0) {
            throw new IllegalArgumentException(
                    "Invalid conversation snapshot.");
        }

        if (reply == null) {
            throw new IllegalArgumentException(
                    "A complete AI reply is required.");
        }

        List<ValidatedAttachment> safeAttachments =
                attachments == null
                        ? List.of()
                        : List.copyOf(attachments);

        validateMessage(request.messageText(), safeAttachments);

        // Serialize saves for this repair using a short database lock.
        RepairJob repair = repairJobRepository
                .findByIdForUpdate(repairId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Repair not found."));

        entityManager.refresh(repair);

        if (!Objects.equals(
                repair.getUpdatedAt(),
                request.expectedUpdatedAt())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This repair changed while the AI request was being "
                            + "processed. Reload the repair before sending again.");
        }

        if (repair.getStatus() == RepairStatus.COMPLETED
                || repair.getStatus() == RepairStatus.CANCELLED) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "New AI messages cannot be added to a completed "
                            + "or cancelled repair.");
        }

        Long latestMessageId = messageRepository
                .findTopByRepairJobIdOrderByIdDesc(repairId)
                .map(AiMessage::getId)
                .orElse(null);

        if (!Objects.equals(
                latestMessageId,
                expectedLastMessageId)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The AI conversation changed while your request was "
                            + "being processed. Reload the conversation "
                            + "before sending again.");
        }

        AiMessage staffMessage = messageRepository.saveAndFlush(
                AiMessage.staffMessage(
                        repairId,
                        senderUserId,
                        request.messageText()));

        for (int index = 0; index < safeAttachments.size(); index++) {
            ValidatedAttachment attachment = safeAttachments.get(index);

            attachmentRepository.save(new AiMessageAttachment(
                    staffMessage.getId(),
                    attachment.originalFilename(),
                    attachment.contentType(),
                    attachment.imageData(),
                    (short) (index + 1)));
        }

        messageRepository.saveAndFlush(
                AiMessage.aiMessage(
                        repairId,
                        reply.messageText(),
                        reply.suggestedDiagnosis(),
                        reply.recommendedParts().toArray(String[]::new),
                        reply.suggestedStatus()));

        attachmentRepository.flush();

        return readService.getMessages(username, repairId);
    }

    private Long requireAuthorizedUser(String username) {
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
                    "You do not have permission to use AI troubleshooting.");
        }

        return userId;
    }

    private void validateMessage(
            String messageText,
            List<ValidatedAttachment> attachments) {

        boolean hasText = messageText != null
                && !messageText.isBlank();

        if (!hasText && attachments.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Enter a message or attach an image.");
        }

        if (messageText != null && messageText.length() > 5000) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Message must not exceed 5000 characters.");
        }

        if (attachments.size() > 3) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "You can attach up to 3 images per message.");
        }
    }
}