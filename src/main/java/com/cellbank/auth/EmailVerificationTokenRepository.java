package com.cellbank.auth;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationTokenRepository
        extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    Optional<EmailVerificationToken>
            findFirstByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    List<EmailVerificationToken> findAllByUserIdAndUsedAtIsNull(
            Long userId);
}
