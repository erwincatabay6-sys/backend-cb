package com.cellbank.backup;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PostgresBackupExporter {

    private static final Logger log =
            LoggerFactory.getLogger(PostgresBackupExporter.class);

    private static final Set<String> SSL_MODES = Set.of(
            "disable",
            "allow",
            "prefer",
            "require",
            "verify-ca",
            "verify-full"
    );

    private final BackupSettings settings;
    private final String databaseUrl;
    private final String username;
    private final String password;

    public PostgresBackupExporter(
            BackupSettings settings,

            @Value("${spring.datasource.url}")
            String databaseUrl,

            @Value("${spring.datasource.username}")
            String username,

            @Value("${spring.datasource.password}")
            String password) {

        this.settings = settings;
        this.databaseUrl = databaseUrl;
        this.username = username;
        this.password = password;
    }

    /**
     * The caller must close the result after storing the archive.
     * Progress messages are safe for the activity panel.
     */
    public ExportResult export(Consumer<String> progress)
            throws IOException {

        if (!settings.isEnabled()) {
            throw new IOException("Backups are disabled.");
        }

        ConnectionInfo connection = parseConnection();

        Path archive = null;
        Path passwordFile = null;
        Path diagnostics = null;
        boolean completed = false;

        try {
            Files.createDirectories(settings.getWorkDirectory());

            archive = Files.createTempFile(
                    settings.getWorkDirectory(),
                    "cellbank-",
                    ".dump"
            );

            passwordFile = Files.createTempFile(
                    settings.getWorkDirectory(),
                    "cellbank-",
                    ".pgpass"
            );

            diagnostics = Files.createTempFile(
                    settings.getWorkDirectory(),
                    "cellbank-",
                    ".log"
            );

            // createTempFile uses owner-only permissions on POSIX systems.
            String passwordEntry = String.join(
                    ":",
                    escapePasswordField(connection.host()),
                    Integer.toString(connection.port()),
                    escapePasswordField(connection.database()),
                    escapePasswordField(username),
                    escapePasswordField(password)
            );

            Files.writeString(
                    passwordFile,
                    passwordEntry + "\n",
                    StandardCharsets.UTF_8
            );

            Map<String, String> environment = Map.of(
                    "PGHOST", connection.host(),
                    "PGPORT", Integer.toString(connection.port()),
                    "PGDATABASE", connection.database(),
                    "PGUSER", username,
                    "PGPASSFILE", passwordFile.toString(),
                    "PGSSLMODE", connection.sslMode(),
                    "PGCONNECT_TIMEOUT", "15",
                    "PGAPPNAME", "cellbank-backup"
            );

            progress.accept("Exporting the database.");

            runProcess(
                    List.of(
                            settings.getPgDumpExecutable(),
                            "--format=custom",
                            "--no-password",
                            "--lock-wait-timeout=10000",
                            "--file=" + archive
                    ),
                    environment,
                    diagnostics,
                    "Database export"
            );

            if (Files.size(archive) == 0) {
                throw new IOException(
                        "Database export produced an empty file."
                );
            }

            progress.accept(
                    "Database export completed. Checking archive readability."
            );

            // No --dbname: generate SQL to discarded stdout only.
            // This reads the archive data without restoring a database.
            runProcess(
                    List.of(
                            settings.getPgRestoreExecutable(),
                            "--file=-",
                            archive.toString()
                    ),
                    Map.of(),
                    diagnostics,
                    "Archive verification"
            );

            progress.accept(
                    "Archive readability check passed. Calculating checksum."
            );

            String checksum = calculateSha256(archive);

            ExportResult result = new ExportResult(
                    archive,
                    Files.size(archive),
                    checksum
            );

            completed = true;
            return result;

        } finally {
            deleteTemporaryFile(passwordFile);
            deleteTemporaryFile(diagnostics);

            if (!completed) {
                deleteTemporaryFile(archive);
            }
        }
    }

    private void runProcess(
            List<String> command,
            Map<String, String> environment,
            Path diagnostics,
            String stage) throws IOException {

        ProcessBuilder builder = new ProcessBuilder(command);

        // Prevent inherited PostgreSQL connection options from changing
        // which database or authentication settings the tools use.
        builder.environment().keySet().removeIf(
                key -> key.startsWith("PG")
        );

        // These unrelated application secrets are not needed by the tools.
        builder.environment().keySet().removeIf(
                key -> key.startsWith("CELLBANK_")
                        || key.startsWith("MAIL_")
                        || key.startsWith("OPENAI_")
                        || key.startsWith("SPRING_")
        );

        builder.environment().putAll(environment);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.redirectError(diagnostics.toFile());

        Process process;

        try {
            process = builder.start();
        } catch (IOException exception) {
            throw new IOException(
                    stage + " could not start. Check the PostgreSQL tool path."
            );
        }

        try {
            process.getOutputStream().close();

            boolean finished = process.waitFor(
                    settings.getProcessTimeout().toSeconds(),
                    TimeUnit.SECONDS
            );

            if (!finished) {
                throw new IOException(
                        stage + " exceeded the configured time limit."
                );
            }

            logDiagnostics(diagnostics, stage);

            if (process.exitValue() != 0) {
                throw new IOException(
                        stage + " failed. Check the backend log."
                );
            }

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new IOException(
                    stage + " was interrupted."
            );

        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();

                try {
                    process.waitFor(5, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private void logDiagnostics(Path diagnostics, String stage)
            throws IOException {

        String message;

        try (InputStream input = Files.newInputStream(diagnostics)) {
            message = new String(
                    input.readNBytes(8192),
                    StandardCharsets.UTF_8
            ).strip();
        }

        if (!message.isBlank()) {
            if (!password.isEmpty()) {
                message = message.replace(password, "[REDACTED]");
            }

            message = message.replace(databaseUrl, "[DATABASE_URL]");

            // Technical details stay in the backend log, not API messages.
            log.warn("{} diagnostics: {}", stage, message);
        }
    }

    private ConnectionInfo parseConnection() throws IOException {
        if (!databaseUrl.startsWith("jdbc:postgresql://")) {
            throw new IOException(
                    "Backup requires a PostgreSQL JDBC URL with a host."
            );
        }

        URI uri;

        try {
            uri = URI.create(databaseUrl.substring("jdbc:".length()));
        } catch (IllegalArgumentException exception) {
            throw new IOException(
                    "The backup database connection URL is invalid."
            );
        }

        String host = uri.getHost();
        String path = uri.getPath();
        int port = uri.getPort() == -1 ? 5432 : uri.getPort();

        if (host == null || host.isBlank()
                || path == null || path.length() <= 1
                || port < 1 || port > 65535
                || uri.getUserInfo() != null
                || uri.getFragment() != null) {
            throw new IOException(
                    "Backup requires a single database host and database name. "
                            + "Keep credentials in the datasource properties."
            );
        }

        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }

        String sslMode = "prefer";
        String query = uri.getRawQuery();

        // Match the current application's URL and allow an explicit SSL mode.
        // Reject unsupported options rather than silently ignoring them.
        if (query != null && !query.isBlank()) {
            if (!query.startsWith("sslmode=")
                    || query.contains("&")) {
                throw new IOException(
                        "Backup currently supports only the sslmode "
                                + "connection URL option."
                );
            }

            sslMode = query.substring("sslmode=".length());

            if (!SSL_MODES.contains(sslMode)) {
                throw new IOException("Unsupported backup SSL mode.");
            }
        }

        return new ConnectionInfo(
                host,
                port,
                path.substring(1),
                sslMode
        );
    }

    private String escapePasswordField(String value) throws IOException {
        if (value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0
                || value.indexOf('\0') >= 0) {
            throw new IOException(
                    "Backup connection settings contain unsupported characters."
            );
        }

        return value.replace("\\", "\\\\")
                .replace(":", "\\:");
    }

    private String calculateSha256(Path archive) throws IOException {
        MessageDigest digest;

        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable.",
                    exception
            );
        }

        try (DigestInputStream input = new DigestInputStream(
                Files.newInputStream(archive),
                digest)) {

            input.transferTo(OutputStream.nullOutputStream());
        }

        return HexFormat.of().formatHex(digest.digest());
    }

    private void deleteTemporaryFile(Path path) {
        if (path == null) {
            return;
        }

        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            log.warn(
                    "A temporary backup file could not be removed. "
                            + "Check the configured backup work directory."
            );
        }
    }

    private record ConnectionInfo(
            String host,
            int port,
            String database,
            String sslMode) {
    }

    public record ExportResult(
            Path file,
            long sizeBytes,
            String sha256) implements AutoCloseable {

        @Override
        public void close() throws IOException {
            Files.deleteIfExists(file);
        }
    }
}
