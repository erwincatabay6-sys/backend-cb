package com.cellbank.ai;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.cellbank.ai.AiAssistantRequest.ConversationMessage;
import com.cellbank.ai.AiAssistantRequest.ImageInput;
import com.cellbank.repair.RepairStatus;

import tools.jackson.databind.json.JsonMapper;

@Component
public class OpenAiRequestBuilder {

    private static final int MAX_OUTPUT_TOKENS = 4000;

    private final OpenAiSettings settings;
    private final JsonMapper jsonMapper;

    public OpenAiRequestBuilder(
            OpenAiSettings settings,
            JsonMapper jsonMapper) {

        this.settings = settings;
        this.jsonMapper = jsonMapper;
    }

    public Map<String, Object> build(AiAssistantRequest request) {
        Objects.requireNonNull(request, "AI request is required.");

        List<RepairStatus> allowedStatuses =
                allowedNextStatuses(request.context().status());

        String instructions = AiAssistantInstructions.SYSTEM_PROMPT
                + "\nAllowed next statuses for this repair: "
                + allowedStatuses
                + ". Use null when no change is justified. "
                + "User permissions will be checked separately when "
                + "a person reviews and saves a status change.";

        List<Map<String, Object>> input = new ArrayList<>();

        // Context remains task data rather than privileged instructions.
        String contextJson = jsonMapper.writeValueAsString(
                request.context());

        input.add(Map.of(
                "role", "user",
                "content", List.of(textContent(
                        "Current saved repair context (JSON data):\n"
                                + contextJson))));

        // The caller supplies messages in chronological order.
        for (ConversationMessage message : request.conversation()) {
            input.add(buildMessage(message));
        }

        Map<String, Object> body = new LinkedHashMap<>();

        body.put("model", settings.getModel());
        body.put("instructions", instructions);
        body.put("input", input);
        body.put("store", false);
        body.put("stream", false);
        body.put("max_output_tokens", MAX_OUTPUT_TOKENS);
        body.put("reasoning", Map.of("effort", "low"));

        body.put("text", Map.of(
                "format", Map.of(
                        "type", "json_schema",
                        "name", "cellbank_troubleshooting_reply",
                        "strict", true,
                        "schema", buildReplySchema(allowedStatuses))));

        return body;
    }

    private Map<String, Object> buildMessage(
            ConversationMessage message) {

        if (message.senderType() == AiMessageSenderType.AI) {
            if (!message.images().isEmpty()) {
                throw new IllegalArgumentException(
                        "Saved AI replies must not contain image inputs.");
            }

            return Map.of(
                    "role", "assistant",
                    "content", message.messageText());
        }

        List<Map<String, Object>> content = new ArrayList<>();

        if (message.messageText() != null) {
            content.add(textContent(message.messageText()));
        }

        for (ImageInput image : message.images()) {
            String encodedImage = Base64.getEncoder()
                    .encodeToString(image.imageData());

            String imageUrl = "data:"
                    + image.contentType()
                    + ";base64,"
                    + encodedImage;

            content.add(Map.of(
                    "type", "input_image",
                    "image_url", imageUrl,
                    "detail", "high"));
        }

        return Map.of(
                "role", "user",
                "content", content);
    }

    private Map<String, Object> textContent(String text) {
        return Map.of(
                "type", "input_text",
                "text", text);
    }

    private Map<String, Object> buildReplySchema(
            List<RepairStatus> allowedStatuses) {

        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("messageText", Map.of(
                "type", "string",
                "minLength", 1,
                "maxLength", 6000));

        properties.put("suggestedDiagnosis", Map.of(
                "type", List.of("string", "null"),
                "maxLength", 2000));

        properties.put("recommendedParts", Map.of(
                "type", "array",
                "maxItems", 5,
                "items", Map.of(
                        "type", "string",
                        "minLength", 1,
                        "maxLength", 150)));

        // ArrayList permits the JSON null value required by this enum.
        List<Object> statusValues = new ArrayList<>();

        for (RepairStatus status : allowedStatuses) {
            statusValues.add(status.name());
        }

        statusValues.add(null);

        properties.put("suggestedStatus", Map.of(
                "type", List.of("string", "null"),
                "enum", statusValues));

        return Map.of(
                "type", "object",
                "properties", properties,
                "required", List.of(
                        "messageText",
                        "suggestedDiagnosis",
                        "recommendedParts",
                        "suggestedStatus"),
                "additionalProperties", false);
    }

    /**
     * Matches the repair lifecycle currently enforced by RepairService.
     * These are possible transitions, not permission to perform them.
     */
    public List<RepairStatus> allowedNextStatuses(
            RepairStatus currentStatus) {

        Objects.requireNonNull(
                currentStatus,
                "Current repair status is required.");

        return switch (currentStatus) {
            case RECEIVED -> List.of(
                    RepairStatus.AWAITING_APPROVAL,
                    RepairStatus.IN_PROGRESS,
                    RepairStatus.CANCELLED);

            case AWAITING_APPROVAL -> List.of(
                    RepairStatus.IN_PROGRESS,
                    RepairStatus.CANCELLED);

            case IN_PROGRESS -> List.of(
                    RepairStatus.AWAITING_APPROVAL,
                    RepairStatus.AWAITING_PARTS,
                    RepairStatus.READY_FOR_RELEASE,
                    RepairStatus.CANCELLED);

            case AWAITING_PARTS -> List.of(
                    RepairStatus.IN_PROGRESS,
                    RepairStatus.READY_FOR_RELEASE,
                    RepairStatus.CANCELLED);

            case READY_FOR_RELEASE -> List.of(
                    RepairStatus.IN_PROGRESS,
                    RepairStatus.COMPLETED);

            case COMPLETED, CANCELLED -> List.of();
        };
    }
}