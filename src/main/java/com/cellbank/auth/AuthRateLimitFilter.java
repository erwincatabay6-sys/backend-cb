package com.cellbank.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

public class AuthRateLimitFilter extends OncePerRequestFilter {

    private final AuthRateLimitService rateLimitService;

    public AuthRateLimitFilter(
            AuthRateLimitService rateLimitService) {

        this.rateLimitService = rateLimitService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getServletPath();
        Policy policy = policyFor(path);

        if (policy == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // Use the connection address.
        // Do not trust client-supplied forwarding headers here.
        String clientAddress = request.getRemoteAddr();

        String ipKey = "auth:ip:"
                + policy.name()
                + ":"
                + clientAddress;

        long retryAfter = rateLimitService.checkAndConsume(
                ipKey,
                policy.maxRequests(),
                policy.duration()
        );

        if (retryAfter > 0) {
            rejectRequest(response, retryAfter);
            return;
        }

        if ("/api/auth/login".equals(path)) {
            String identifier = request.getParameter("identifier");

            if (identifier != null) {
                identifier = identifier.strip();

                if (identifier.length() > 254) {
                    response.setStatus(
                            HttpServletResponse.SC_BAD_REQUEST
                    );
                    response.setCharacterEncoding(
                            StandardCharsets.UTF_8.name()
                    );
                    response.setContentType("application/json");
                    response.setHeader("Cache-Control", "no-store");

                    response.getWriter().write(
                            "{\"message\":\"Login identifier is too long.\"}"
                    );
                    return;
                }

                if (!identifier.isBlank()) {
                    String identifierKey = "auth:login:identifier:"
                            + identifier.toLowerCase(Locale.ROOT);

                    retryAfter = rateLimitService.checkAndConsume(
                            identifierKey,
                            10,
                            Duration.ofMinutes(15)
                    );

                    if (retryAfter > 0) {
                        rejectRequest(response, retryAfter);
                        return;
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private Policy policyFor(String path) {
        return switch (path) {
            case "/api/auth/login" ->
                    new Policy(
                            "login",
                            30,
                            Duration.ofMinutes(5)
                    );

            case "/api/auth/forgot-password" ->
                    new Policy(
                            "recovery",
                            10,
                            Duration.ofMinutes(15)
                    );

            case "/api/auth/me/email-verification" ->
                    new Policy(
                            "send-verification",
                            10,
                            Duration.ofMinutes(15)
                    );

            case "/api/auth/reset-password" ->
                    new Policy(
                            "reset-password",
                            20,
                            Duration.ofMinutes(15)
                    );

            case "/api/auth/verify-email" ->
                    new Policy(
                            "verify-email",
                            30,
                            Duration.ofMinutes(15)
                    );

            case "/api/auth/change-password" ->
                    new Policy(
                            "change-password",
                            10,
                            Duration.ofMinutes(15)
                    );

            default -> null;
        };
    }

    private void rejectRequest(
            HttpServletResponse response,
            long retryAfter) throws IOException {

        response.setStatus(429);
        response.setCharacterEncoding(
                StandardCharsets.UTF_8.name()
        );
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader(
                "Retry-After",
                Long.toString(retryAfter)
        );

        response.getWriter().write(
                """
                {
                  "message": "Too many requests. Please wait before trying again.",
                  "retryAfterSeconds": %d
                }
                """.formatted(retryAfter)
        );
    }

    private record Policy(
            String name,
            int maxRequests,
            Duration duration) {
    }
}