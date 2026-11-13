package com.cellbank.tracking;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.cellbank.auth.AuthRateLimitService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

public class TrackingRateLimitFilter extends OncePerRequestFilter {

    private final AuthRateLimitService rateLimitService;

    public TrackingRateLimitFilter(
            AuthRateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        boolean trackingRequest =
                "GET".equalsIgnoreCase(request.getMethod())
                && request.getServletPath()
                        .startsWith("/api/tracking/");

        if (!trackingRequest) {
            filterChain.doFilter(request, response);
            return;
        }

        // Do not read client-supplied forwarding headers directly.
        String key = "tracking:ip:" + request.getRemoteAddr();

        long retryAfter = rateLimitService.checkAndConsume(
                key,
                30,
                Duration.ofMinutes(5)
        );

        if (retryAfter == 0) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(429);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Retry-After", Long.toString(retryAfter));

        response.getWriter().write(
                """
                {
                  "message": "Too many tracking requests. Please wait before trying again.",
                  "retryAfterSeconds": %d
                }
                """.formatted(retryAfter)
        );
    }
}
