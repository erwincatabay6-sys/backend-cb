package com.cellbank.auth;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PendingEmailChangeRepository
        extends JpaRepository<PendingEmailChange, Long> {
}