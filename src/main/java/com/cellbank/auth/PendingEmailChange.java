package com.cellbank.auth;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "pending_email_changes", schema = "public")
public class PendingEmailChange {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "proposed_email", nullable = false, length = 254)
    private String proposedEmail;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    protected PendingEmailChange() {
        // Required by JPA.
    }

    public PendingEmailChange(Long userId, String proposedEmail,
            String codeHash, Instant issuedAt, Instant expiresAt) {
        this.userId = userId;
        this.proposedEmail = proposedEmail;
        this.codeHash = codeHash;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.failedAttempts = 0;
    }

    public Long getUserId() {
        return userId;
    }

    public String getProposedEmail() {
        return proposedEmail;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }
    
    public void renewCode(String codeHash, Instant issuedAt, Instant expiresAt) {
        this.codeHash = codeHash;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public void recordFailedAttempt() {
        failedAttempts++;
    }
}