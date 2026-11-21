package com.cellbank.auth;

import java.time.Instant;

public record PendingEmailChangeStatusResponse(
        boolean pending,
        String maskedProposedEmail,
        Instant expiresAt,
        Instant resendAvailableAt,
        Instant serverTime
) {
}