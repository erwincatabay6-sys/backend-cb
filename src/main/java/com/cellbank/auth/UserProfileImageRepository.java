package com.cellbank.auth;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileImageRepository
        extends JpaRepository<UserProfileImage, Long> {
}
