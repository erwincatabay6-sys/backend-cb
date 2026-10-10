package com.cellbank.technician;

import com.cellbank.auth.UserStatus;

public record TechnicianResponse(
        Long id,
        String name,
        String role,
        UserStatus status,
        long activeRepairs,
        long totalRepairs
) {
}
