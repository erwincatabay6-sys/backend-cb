package com.cellbank.ai;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OpenAiSettings {

    private final boolean enabled;
    private final String apiKey;
    private final String model;
    private final Duration connectTimeout;
    private final Duration requestTimeout;

    public OpenAiSettings(
            @Value("${cellbank.ai.openai.enabled:false}")
            boolean enabled,

            @Value("${cellbank.ai.openai.api-key:}")
            String apiKey,

            @Value("${cellbank.ai.openai.model:gpt-6.1-sol}")
            String model,

            @Value("${cellbank.ai.openai.connect-timeout-seconds:10}")
            long connectTimeoutSeconds,

            @Value("${cellbank.ai.openai.request-timeout-seconds:90}")
            long requestTimeoutSeconds) {

        if (connectTimeoutSeconds <= 0 || requestTimeoutSeconds <= 0) {
            throw new IllegalArgumentException(
                    "OpenAI timeouts must be greater than zero.");
        }

        this.enabled = enabled;
        this.apiKey = apiKey == null ? "" : apiKey.strip();
        this.model = model == null ? "" : model.strip();
        this.connectTimeout = Duration.ofSeconds(connectTimeoutSeconds);
        this.requestTimeout = Duration.ofSeconds(requestTimeoutSeconds);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isConfigured() {
        return !apiKey.isBlank() && !model.isBlank();
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getModel() {
        return model;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    @Override
    public String toString() {
        return "OpenAiSettings[enabled="
                + enabled
                + ", configured="
                + isConfigured()
                + ", model="
                + model
                + ", apiKey=<redacted>]";
    }
}
