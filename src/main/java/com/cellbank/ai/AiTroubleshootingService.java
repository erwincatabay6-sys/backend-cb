package com.cellbank.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.cellbank.ai.AiAssistantRequest.ConversationMessage;
import com.cellbank.ai.AiAssistantRequest.ImageInput;
import com.cellbank.repair.RepairStatus;

import tools.jackson.databind.json.JsonMapper;

@Service
@PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
public class AiTroubleshootingService {

    private final AiTroubleshootingContextService contextService;
    private final AiMessageRepository messageRepository;
    private final AiMessageAttachmentRepository attachmentRepository;
    private final AiMessageAttachmentValidator attachmentValidator;
    private final AiAssistantProvider assistantProvider;
    private final AiMessageWriteService writeService;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate snapshotTransaction;

    public AiTroubleshootingService(
            AiTroubleshootingContextService contextService,
            AiMessageRepository messageRepository,
            AiMessageAttachmentRepository attachmentRepository,
            AiMessageAttachmentValidator attachmentValidator,
            AiAssistantProvider assistantProvider,
            AiMessageWriteService writeService,
            JsonMapper jsonMapper,
            PlatformTransactionManager transactionManager) {

        this.contextService = contextService;
        this.messageRepository = messageRepository;
        this.attachmentRepository = attachmentRepository;
        this.attachmentValidator = attachmentValidator;
        this.assistantProvider = assistantProvider;
        this.writeService = writeService;
        this.jsonMapper = jsonMapper;

        this.snapshotTransaction =
                new TransactionTemplate(transactionManager);

        this.snapshotTransaction.setReadOnly(true);
        this.snapshotTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.snapshotTransaction.setIsolationLevel(
                TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    /**
     * Database reads finish before contacting the AI provider.
     * The write service starts a separate, short transaction afterward.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<AiMessageResponse> sendMessage(
            String username,
            Long repairId,
            AiMessageCreateRequest request,
            List<MultipartFile> attachments) {

        validateRequest(request, attachments);

        ConversationSnapshot snapshot = Objects.requireNonNull(
                snapshotTransaction.execute(
                        transactionStatus -> loadSnapshot(
                                username,
                                repairId,
                                request)));

        var validatedAttachments = attachmentValidator.validate(
                attachments);

        List<ImageInput> currentImages = validatedAttachments.stream()
                .map(attachment -> new ImageInput(
                        attachment.contentType(),
                        attachment.imageData()))
                .toList();

        List<ConversationMessage> conversation =
                new ArrayList<>(snapshot.conversation());

        conversation.add(new ConversationMessage(
                AiMessageSenderType.STAFF,
                request.messageText(),
                currentImages));

        AiAssistantRequest providerRequest = new AiAssistantRequest(
                snapshot.context(),
                conversation);

        // No database transaction is held during this network call.
        AiAssistantReply reply =
                assistantProvider.generateReply(providerRequest);

        // Rechecks permissions, repair state, and conversation freshness.
        return writeService.saveExchange(
                username,
                repairId,
                request,
                snapshot.lastMessageId(),
                validatedAttachments,
                reply);
    }

    private ConversationSnapshot loadSnapshot(
            String username,
            Long repairId,
            AiMessageCreateRequest request) {

        // Also verifies the account is active and has an authorized role.
        AiTroubleshootingContext context =
                contextService.buildContext(username, repairId);

        if (!Objects.equals(
                context.repairUpdatedAt(),
                request.expectedUpdatedAt())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This repair changed since you opened it. "
                            + "Reload the repair before sending a message.");
        }

        if (context.status() == RepairStatus.COMPLETED
                || context.status() == RepairStatus.CANCELLED) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "New AI messages cannot be added to a completed "
                            + "or cancelled repair.");
        }

        List<AiMessage> savedMessages = messageRepository
                .findByRepairJobIdOrderByCreatedAtAscIdAsc(repairId);

        List<Long> messageIds = savedMessages.stream()
                .map(AiMessage::getId)
                .toList();

        Map<Long, List<ImageInput>> imagesByMessage = new HashMap<>();

        if (!messageIds.isEmpty()) {
            var savedAttachments = attachmentRepository
                    .findAllByAiMessageIdInOrderByAiMessageIdAscDisplayOrderAsc(
                            messageIds);

            for (AiMessageAttachment attachment : savedAttachments) {
                imagesByMessage
                        .computeIfAbsent(
                                attachment.getAiMessageId(),
                                ignored -> new ArrayList<>())
                        .add(new ImageInput(
                                attachment.getContentType(),
                                attachment.getImageData()));
            }
        }

        List<ConversationMessage> conversation = new ArrayList<>();

        for (AiMessage message : savedMessages) {
            String messageText = message.getMessageText();

            if (message.getSenderType() == AiMessageSenderType.AI) {
                // Preserve previous structured suggestions as well as text.
                AiAssistantReply previousReply = new AiAssistantReply(
                        message.getMessageText(),
                        message.getSuggestedDiagnosis(),
                        Arrays.stream(message.getRecommendedParts())
                                .filter(Objects::nonNull)
                                .map(String::strip)
                                .filter(part -> !part.isEmpty())
                                .distinct()
                                .toList(),
                        message.getSuggestedStatus());

                messageText = jsonMapper.writeValueAsString(previousReply);
            }

            conversation.add(new ConversationMessage(
                    message.getSenderType(),
                    messageText,
                    imagesByMessage.getOrDefault(
                            message.getId(),
                            List.of())));
        }

        Long lastMessageId = messageIds.stream()
                .max(Long::compareTo)
                .orElse(null);

        return new ConversationSnapshot(
                context,
                List.copyOf(conversation),
                lastMessageId);
    }

    private void validateRequest(
            AiMessageCreateRequest request,
            List<MultipartFile> attachments) {

        if (request == null || request.expectedUpdatedAt() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Reload the repair before sending a message.");
        }

        String messageText = request.messageText();

        if (messageText != null && messageText.length() > 5000) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Message must not exceed 5000 characters.");
        }

        boolean hasText = messageText != null
                && !messageText.isBlank();

        boolean hasAttachments = attachments != null
                && !attachments.isEmpty();

        if (!hasText && !hasAttachments) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Enter a message or attach an image.");
        }

        if (hasAttachments && attachments.size() > 3) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "You can attach up to 3 images per message.");
        }
    }

    private record ConversationSnapshot(
            AiTroubleshootingContext context,
            List<ConversationMessage> conversation,
            Long lastMessageId) {
    }
}