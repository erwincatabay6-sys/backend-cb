package com.cellbank.backup;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BackupExecutionLock {

    private static final Logger log =
            LoggerFactory.getLogger(BackupExecutionLock.class);

    private final String databaseUrl;
    private final String username;
    private final String password;

    public BackupExecutionLock(
            @Value("${spring.datasource.url}") String databaseUrl,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {

        this.databaseUrl = databaseUrl;
        this.username = username;
        this.password = password;
    }

    /**
     * Returns null if another backup owns the lock.
     * Uses a dedicated connection, never a pooled connection.
     */
    public Handle tryAcquire() throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", username);
        properties.setProperty("password", password);
        properties.setProperty("connectTimeout", "10");
        properties.setProperty("socketTimeout", "10");
        properties.setProperty("tcpKeepAlive", "true");
        properties.setProperty("ApplicationName", "cellbank-backup-lock");

        Connection connection =
                DriverManager.getConnection(databaseUrl, properties);

        boolean acquired = false;

        try {
            connection.setAutoCommit(true);

            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT pg_try_advisory_lock(112841, 1)")) {

                statement.setQueryTimeout(5);

                try (ResultSet result = statement.executeQuery()) {
                    acquired = result.next() && result.getBoolean(1);
                }
            }

            return acquired ? new Handle(connection) : null;

        } finally {
            if (!acquired) {
                connection.close();
            }
        }
    }

    public static final class Handle implements AutoCloseable {

        private final Connection connection;

        private Handle(Connection connection) {
            this.connection = connection;
        }

        public void verify() {
            try {
                // This dedicated connection never unlocks or reconnects.
                if (connection.isClosed() || !connection.isValid(5)) {
                    throw new IllegalStateException(
                            "The backup execution lock was lost."
                    );
                }
            } catch (SQLException exception) {
                throw new IllegalStateException(
                        "The backup execution lock could not be verified."
                );
            }
        }

        @Override
        public void close() {
            try {
                connection.close();
            } catch (SQLException exception) {
                log.warn("The backup lock connection could not close normally.");
            }
        }
    }
}