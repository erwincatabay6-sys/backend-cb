package com.cellbank.repair;

import com.cellbank.auth.User;

public record TechnicianOptionResponse(
        Long id,
        String name
) {

    public static TechnicianOptionResponse from(User user) {
        return new TechnicianOptionResponse(
                user.getId(),
                user.getFullName()
        );
    }
}