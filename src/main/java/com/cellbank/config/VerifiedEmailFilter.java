package com.cellbank.config;

import java.io.IOException;
import java.util.Optional;

import com.cellbank.auth.UserRepository;
import com.cellbank.auth.UserStatus;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

final class VerifiedEmailFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final RequestMatcher availableBeforeVerification;

    VerifiedEmailFilter(UserRepository userRepository) {
        this.userRepository = userRepository;

        var routes = PathPatternRequestMatcher.withDefaults();

        this.availableBeforeVerification = new OrRequestMatcher(
                routes.matcher(HttpMethod.GET, "/api/health"),
                routes.matcher(HttpMethod.GET, "/api/tracking/{trackingCode}"),
                routes.matcher(HttpMethod.GET, "/api/auth/csrf"),
                routes.matcher(HttpMethod.GET, "/api/auth/me"),
                routes.matcher(HttpMethod.GET, "/api/auth/me/email-verification"),
                routes.matcher(HttpMethod.POST, "/api/auth/me/email-verification"),
                routes.matcher(HttpMethod.POST, "/api/auth/verify-email"),
                routes.matcher(HttpMethod.POST, "/api/auth/forgot-password"),
                routes.matcher(HttpMethod.POST, "/api/auth/reset-password")
        );
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return availableBeforeVerification.matches(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        Optional<Boolean> verified =
                userRepository.findEmailVerifiedByUsernameAndStatus(
                        authentication.getName(),
                        UserStatus.ACTIVE
                );

        if (verified.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        if (!verified.get()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"code\":\"EMAIL_VERIFICATION_REQUIRED\"}"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }
}