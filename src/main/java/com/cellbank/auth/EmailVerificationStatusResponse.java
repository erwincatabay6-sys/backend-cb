package com.cellbank.auth;

import java.time.Instant;

public record EmailVerificationStatusResponse(
        boolean emailVerified,
        Instant serverTime,
        Instant resendAvailableAt
) {
}
