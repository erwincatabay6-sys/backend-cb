package com.cellbank.auth;

import java.util.List;

public record CurrentUserResponse(
        Long id,
        String name,
        String username,
        String email,
        List<String> roles,
        UserStatus status,
        boolean emailVerified,
        String profileImageUrl
) {

    public CurrentUserResponse {
        roles = List.copyOf(roles);
    }
}
