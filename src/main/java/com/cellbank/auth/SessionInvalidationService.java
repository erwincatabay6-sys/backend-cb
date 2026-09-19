package com.cellbank.auth;

import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class SessionInvalidationService {

    private final SessionRegistry sessionRegistry;

    public SessionInvalidationService(
            SessionRegistry sessionRegistry) {

        this.sessionRegistry = sessionRegistry;
    }

    public void expireSessionsAfterCommit(String username) {

        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager
                        .isSynchronizationActive()) {

            throw new IllegalStateException(
                    "Session expiration must be scheduled "
                            + "inside an active transaction."
            );
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCommit() {
                        expireSessions(username);
                    }
                }
        );
    }

    private void expireSessions(String username) {

        for (Object principal : sessionRegistry.getAllPrincipals()) {

            if (principal instanceof UserDetails userDetails
                    && userDetails.getUsername()
                            .equalsIgnoreCase(username)) {

                for (SessionInformation session :
                        sessionRegistry.getAllSessions(principal, false)) {

                    session.expireNow();
                }
            }
        }
    }
}