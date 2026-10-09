package com.cellbank.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.cellbank.repair.RepairStatus;

import tools.jackson.databind.json.JsonMapper;

@Component
public class OpenAiAssistantProvider implements AiAssistantProvider {

    private static final URI RESPONSES_ENDPOINT =
            URI.create("https://api.openai.com/v1/responses");

    private static final Set<String> REPLY_FIELDS = Set.of(
            "messageText",
            "suggestedDiagnosis",
            "recommendedParts",
            "suggestedStatus");

    private static final Set<String> BILLING_ERROR_CODES = Set.of(
            "credit_balance_exhausted",
            "insufficient_quota",
            "billing_hard_limit_reached",
            "organization_spend_limit_exceeded",
            "project_spend_limit_exceeded",
            "organization_usage_limit_exceeded");

    private final OpenAiSettings settings;
    private final OpenAiRequestBuilder requestBuilder;
    private final JsonMapper jsonMapper;
    private final HttpClient httpClient;

    public OpenAiAssistantProvider(
            OpenAiSettings settings,
            OpenAiRequestBuilder requestBuilder,
            JsonMapper jsonMapper) {

        this.settings = settings;
        this.requestBuilder = requestBuilder;
        this.jsonMapper = jsonMapper;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(settings.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public boolean isTestMode() {
        return false;
    }

    @Override
    public AiAssistantReply generateReply(AiAssistantRequest request) {
        if (!settings.isEnabled()) {
            throw failure(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI troubleshooting is not enabled yet.");
        }

        if (!settings.isConfigured()) {
            throw failure(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI troubleshooting is not configured. "
                            + "Please contact the administrator.");
        }

        try {
            String requestBody = jsonMapper.writeValueAsString(
                    requestBuilder.build(request));

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(RESPONSES_ENDPOINT)
                    .timeout(settings.getRequestTimeout())
                    .header(
                            "Authorization",
                            "Bearer " + settings.getApiKey())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            requestBody,
                            StandardCharsets.UTF_8))
                    .build();

            // No automatic application-level retries.
            HttpResponse<String> response = httpClient.send(
                    httpRequest,
                    HttpResponse.BodyHandlers.ofString(
                            StandardCharsets.UTF_8));

            if (response.statusCode() < 200
                    || response.statusCode() >= 300) {
                throw providerError(
                        response.statusCode(),
                        response.body());
            }

            return parseResponse(response.body(), request);

        } catch (ResponseStatusException exception) {
            throw exception;

        } catch (HttpTimeoutException exception) {
            throw failure(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "The AI request timed out. "
                            + "No complete reply was received.");

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw failure(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "The AI request was interrupted.");

        } catch (IOException exception) {
            throw failure(
                    HttpStatus.BAD_GATEWAY,
                    "The server could not complete its connection "
                            + "to the AI provider. Please try again later.");

        } catch (RuntimeException exception) {
            // Do not expose raw provider responses, images, or credentials.
            throw failure(
                    HttpStatus.BAD_GATEWAY,
                    "The AI request could not be processed correctly. "
                            + "Please contact the administrator if this continues.");
        }
    }

    private AiAssistantReply parseResponse(
            String responseBody,
            AiAssistantRequest request) {

        Map<?, ?> response = readObject(responseBody);
        String status = text(response.get("status"));

        if ("incomplete".equals(status)) {
            Map<?, ?> details = objectOrEmpty(
                    response.get("incomplete_details"));

            String reason = text(details.get("reason"));

            if ("max_output_tokens".equals(reason)) {
                throw failure(
                        HttpStatus.BAD_GATEWAY,
                        "The AI reached its response token limit before "
                                + "finishing. No complete reply was returned. "
                                + "Try a more focused question.");
            }

            if ("content_filter".equals(reason)) {
                throw failure(
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        "The AI could not complete this request "
                                + "because of a content restriction.");
            }

            throw failure(
                    HttpStatus.BAD_GATEWAY,
                    "The AI returned an incomplete response. "
                            + "No suggestions were accepted.");
        }

        if (!"completed".equals(status)) {
            throw failure(
                    HttpStatus.BAD_GATEWAY,
                    "The AI did not finish generating a response.");
        }

        Object outputValue = response.get("output");

        if (!(outputValue instanceof List<?> output)) {
            throw invalidReply();
        }

        StringBuilder replyJson = new StringBuilder();

        for (Object itemValue : output) {
            Map<?, ?> item = objectOrEmpty(itemValue);

            if (!"message".equals(text(item.get("type")))) {
                continue;
            }

            if (!"assistant".equals(text(item.get("role")))) {
                continue;
            }

            Object contentValue = item.get("content");

            if (!(contentValue instanceof List<?> contents)) {
                throw invalidReply();
            }

            for (Object contentItem : contents) {
                Map<?, ?> content = objectOrEmpty(contentItem);
                String type = text(content.get("type"));

                if ("refusal".equals(type)) {
                    throw failure(
                            HttpStatus.UNPROCESSABLE_CONTENT,
                            "The AI declined this request. "
                                    + "Rephrase it around the device's "
                                    + "repair symptoms and diagnostic checks.");
                }

                if ("output_text".equals(type)) {
                    Object value = content.get("text");

                    if (!(value instanceof String outputText)) {
                        throw invalidReply();
                    }

                    replyJson.append(outputText);
                }
            }
        }

        if (replyJson.isEmpty()) {
            throw invalidReply();
        }

        return validateReply(
                readObject(replyJson.toString()),
                request.context().status());
    }

    private AiAssistantReply validateReply(
            Map<?, ?> reply,
            RepairStatus currentStatus) {

        if (!reply.keySet().equals(REPLY_FIELDS)) {
            throw invalidReply();
        }

        String messageText = validatedText(
                reply.get("messageText"),
                6000,
                false);

        String diagnosis = validatedText(
                reply.get("suggestedDiagnosis"),
                2000,
                true);

        Object partsValue = reply.get("recommendedParts");

        if (!(partsValue instanceof List<?> parts)
                || parts.size() > 5) {
            throw invalidReply();
        }

        List<String> recommendedParts = new ArrayList<>();

        for (Object part : parts) {
            recommendedParts.add(validatedText(part, 150, false));
        }

        RepairStatus suggestedStatus = null;
        Object statusValue = reply.get("suggestedStatus");

        if (statusValue != null) {
            if (!(statusValue instanceof String statusText)) {
                throw invalidReply();
            }

            try {
                suggestedStatus = RepairStatus.valueOf(statusText);
            } catch (IllegalArgumentException exception) {
                throw invalidReply();
            }

            if (!requestBuilder.allowedNextStatuses(currentStatus)
                    .contains(suggestedStatus)) {
                throw invalidReply();
            }
        }

        return new AiAssistantReply(
                messageText,
                diagnosis,
                recommendedParts,
                suggestedStatus);
    }

    private String validatedText(
            Object value,
            int maximumLength,
            boolean nullable) {

        if (value == null && nullable) {
            return null;
        }

        if (!(value instanceof String stringValue)) {
            throw invalidReply();
        }

        if (stringValue.codePointCount(0, stringValue.length())
                > maximumLength) {
            throw invalidReply();
        }

        String normalized = stringValue.strip();

        if (normalized.isEmpty()) {
            if (nullable) {
                return null;
            }

            throw invalidReply();
        }

        return normalized;
    }

    private ResponseStatusException providerError(
            int statusCode,
            String responseBody) {

        String code = "";
        String type = "";

        try {
            Map<?, ?> response = readObject(responseBody);
            Map<?, ?> error = objectOrEmpty(response.get("error"));

            code = text(error.get("code"));
            type = text(error.get("type"));
        } catch (RuntimeException ignored) {
            // An upstream proxy may return HTML or an empty error body.
            // Fall back to the HTTP status without exposing that body.
        }

        if (BILLING_ERROR_CODES.contains(code)
                || "insufficient_quota".equals(type)) {
            return failure(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "The AI account has exhausted its credits or reached "
                            + "a billing or usage limit. "
                            + "Please contact the administrator.");
        }

        if ("context_length_exceeded".equals(code)) {
            return failure(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "The conversation and repair context exceed the "
                            + "AI model's input limit. The administrator "
                            + "must review the context being sent.");
        }

        if (statusCode == 429) {
            return failure(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "The AI provider's request or token rate limit "
                            + "was reached. Please wait before trying again.");
        }

        if (statusCode == 401 || statusCode == 403) {
            return failure(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "The AI provider rejected the server's credentials "
                            + "or access permissions. "
                            + "Please contact the administrator.");
        }

        if ("model_not_found".equals(code) || statusCode == 404) {
            return failure(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "The configured AI model is unavailable "
                            + "or this account cannot access it.");
        }

        if (statusCode == 413) {
            return failure(
                    HttpStatus.CONTENT_TOO_LARGE,
                    "The AI request is too large to process.");
        }

        if (statusCode == 408 || statusCode == 504) {
            return failure(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "The AI provider timed out while processing the request.");
        }

        if (statusCode >= 500) {
            return failure(
                    HttpStatus.BAD_GATEWAY,
                    "The AI provider is temporarily unavailable. "
                            + "Please try again later.");
        }

        return failure(
                HttpStatus.BAD_GATEWAY,
                "The AI provider rejected the request. "
                        + "Please contact the administrator "
                        + "to check the integration settings.");
    }

    private Map<?, ?> readObject(String json) {
        Object value = jsonMapper.readValue(json, Object.class);

        if (!(value instanceof Map<?, ?> object)) {
            throw invalidReply();
        }

        return object;
    }

    private Map<?, ?> objectOrEmpty(Object value) {
        return value instanceof Map<?, ?> object
                ? object
                : Map.of();
    }

    private String text(Object value) {
        return value instanceof String stringValue
                ? stringValue
                : "";
    }

    private ResponseStatusException invalidReply() {
        return failure(
                HttpStatus.BAD_GATEWAY,
                "The AI returned an invalid reply. "
                        + "No suggestions were accepted.");
    }

    private ResponseStatusException failure(
            HttpStatus status,
            String message) {

        return new ResponseStatusException(status, message);
    }
}
