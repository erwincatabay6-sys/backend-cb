package com.cellbank.ai;

import java.util.List;
import java.util.Objects;

public record AiAssistantRequest(
        AiTroubleshootingContext context,
        List<ConversationMessage> conversation) {

    public AiAssistantRequest {
        Objects.requireNonNull(context, "Repair context is required.");

        conversation = conversation == null
                ? List.of()
                : List.copyOf(conversation);

        if (conversation.isEmpty()) {
            throw new IllegalArgumentException(
                    "The conversation must contain a staff message.");
        }

        ConversationMessage latestMessage =
                conversation.get(conversation.size() - 1);

        if (latestMessage.senderType() != AiMessageSenderType.STAFF) {
            throw new IllegalArgumentException(
                    "The latest conversation message must be from staff.");
        }
    }

    public record ConversationMessage(
            AiMessageSenderType senderType,
            String messageText,
            List<ImageInput> images) {

        public ConversationMessage {
            Objects.requireNonNull(
                    senderType,
                    "The message sender type is required.");

            messageText = normalize(messageText);

            images = images == null
                    ? List.of()
                    : List.copyOf(images);

            if (messageText == null && images.isEmpty()) {
                throw new IllegalArgumentException(
                        "A conversation message requires text or an image.");
            }

            if (senderType == AiMessageSenderType.AI
                    && messageText == null) {
                throw new IllegalArgumentException(
                        "An AI conversation message requires text.");
            }
        }

        @Override
        public String toString() {
            return "ConversationMessage[senderType="
                    + senderType
                    + ", messageText=<redacted>"
                    + ", imageCount="
                    + images.size()
                    + "]";
        }
    }

    /**
     * Image bytes must come from validated uploads or saved attachments.
     * This is an internal provider input, not an HTTP request DTO.
     */
    public record ImageInput(
            String contentType,
            byte[] imageData) {

        public ImageInput {
            if (!"image/jpeg".equals(contentType)
                    && !"image/png".equals(contentType)
                    && !"image/webp".equals(contentType)) {
                throw new IllegalArgumentException(
                        "Unsupported image content type.");
            }

            if (imageData == null || imageData.length == 0) {
                throw new IllegalArgumentException(
                        "Image data is required.");
            }

            imageData = imageData.clone();
        }

        @Override
        public byte[] imageData() {
            return imageData.clone();
        }

        @Override
        public String toString() {
            return "ImageInput[contentType="
                    + contentType
                    + ", fileSize="
                    + imageData.length
                    + "]";
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.strip();

        return normalized.isEmpty() ? null : normalized;
    }

    @Override
    public String toString() {
        return "AiAssistantRequest[repairJobId="
                + context.repairJobId()
                + ", conversationMessageCount="
                + conversation.size()
                + "]";
    }
}