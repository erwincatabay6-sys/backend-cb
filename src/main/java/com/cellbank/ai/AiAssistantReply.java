package com.cellbank.ai;

import java.util.List;
import java.util.Objects;

import com.cellbank.repair.RepairStatus;

public record AiAssistantReply(
        String messageText,
        String suggestedDiagnosis,
        List<String> recommendedParts,
        RepairStatus suggestedStatus) {

    public AiAssistantReply {
        messageText = normalize(messageText);

        if (messageText == null) {
            throw new IllegalArgumentException(
                    "An AI assistant reply must contain message text.");
        }

        suggestedDiagnosis = normalize(suggestedDiagnosis);

        recommendedParts = recommendedParts == null
                ? List.of()
                : recommendedParts.stream()
                        .filter(Objects::nonNull)
                        .map(String::strip)
                        .filter(part -> !part.isEmpty())
                        .distinct()
                        .toList();
    }

    public static AiAssistantReply messageOnly(String messageText) {
        return new AiAssistantReply(
                messageText,
                null,
                List.of(),
                null);
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
        return "AiAssistantReply[messageText=<redacted>"
                + ", hasSuggestedDiagnosis="
                + (suggestedDiagnosis != null)
                + ", recommendedPartsCount="
                + recommendedParts.size()
                + ", suggestedStatus="
                + suggestedStatus
                + "]";
    }
}
