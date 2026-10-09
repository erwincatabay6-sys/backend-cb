package com.cellbank.ai;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AiMessageCreateRequest(

        @Size(
                max = 5000,
                message = "Message must not exceed 5000 characters."
        )
        String messageText,

        @NotNull(
                message = "Reload the repair before sending a message."
        )
        Instant expectedUpdatedAt

) {

    public AiMessageCreateRequest {
        if (messageText != null) {
            messageText = messageText.strip();

            if (messageText.isEmpty()) {
                messageText = null;
            }
        }
    }

    @Override
    public String toString() {
        return "AiMessageCreateRequest[redacted]";
    }
}