package com.cellbank.auth;

import java.util.List;

public record StaffAccountResponse(
        Long id,
        String name,
        String username,
        String email,
        List<String> roles,
        UserStatus status
) {
    public StaffAccountResponse {
        roles = List.copyOf(roles);
    }
}
