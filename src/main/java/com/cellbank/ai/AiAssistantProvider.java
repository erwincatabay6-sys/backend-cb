package com.cellbank.ai;

public interface AiAssistantProvider {

    /**
     * Generates a reply from the supplied repair context,
     * conversation, and validated images.
     *
     * Implementations must not modify repair records.
     * Suggestions require separate human review and confirmation.
     */
    AiAssistantReply generateReply(AiAssistantRequest request);

    /**
     * Identifies a test implementation so the application can
     * clearly distinguish test responses from actual AI output.
     */
    boolean isTestMode();
}