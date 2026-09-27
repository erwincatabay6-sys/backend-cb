package com.cellbank.auth;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository
        extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    Optional<PasswordResetToken>
            findFirstByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    List<PasswordResetToken> findAllByUserIdAndUsedAtIsNull(
            Long userId);
}