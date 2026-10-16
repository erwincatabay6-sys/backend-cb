package com.cellbank.backup;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.cellbank.backup.BackupRun.StorageProvider;

@Component
@ConditionalOnProperty(
        name = "cellbank.backup.storage-provider",
        havingValue = "LOCAL",
        matchIfMissing = true
)
public class LocalBackupStorage implements BackupStorage {

    private final BackupSettings settings;

    public LocalBackupStorage(BackupSettings settings) {
        this.settings = settings;
    }

    @Override
    public StorageProvider getProvider() {
        return StorageProvider.LOCAL;
    }

    @Override
    public StoredBackup store(
            String objectKey,
            Path source,
            long expectedSize,
            String expectedSha256) throws IOException {

        validateObjectKey(objectKey);

        if (expectedSize <= 0
                || expectedSha256 == null
                || !expectedSha256.matches("[0-9a-f]{64}")) {
            throw new IOException(
                    "The exported backup metadata is invalid."
            );
        }

        Path directory = storageDirectory();
        Path target = directory.resolve(objectKey);

        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(
                    "A backup already exists with this filename."
            );
        }

        Path temporary = Files.createTempFile(
                directory,
                ".pending-",
                ".tmp"
        );

        try {
            // Write into a private staging file before publishing it.
            try (InputStream input = Files.newInputStream(
                    source,
                    LinkOption.NOFOLLOW_LINKS);
                 OutputStream output = Files.newOutputStream(
                         temporary,
                         StandardOpenOption.WRITE,
                         StandardOpenOption.TRUNCATE_EXISTING)) {

                input.transferTo(output);
            }

            // Re-read the stored copy rather than trusting the source hash.
            long storedSize = Files.size(temporary);
            String storedSha256 = calculateSha256(temporary);

            if (storedSize != expectedSize
                    || !storedSha256.equals(expectedSha256)) {
                throw new IOException(
                        "The stored backup failed its integrity check."
                );
            }

            // No REPLACE_EXISTING: an older file must never be overwritten.
            Files.move(temporary, target);

            return new StoredBackup(
                    objectKey,
                    storedSize,
                    storedSha256
            );

        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @Override
    public InputStream open(String objectKey) throws IOException {
        Path file = resolveObject(objectKey);

        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(
                    "The backup file is unavailable."
            );
        }

        // The caller owns and must close this stream.
        return Channels.newInputStream(
                Files.newByteChannel(
                        file,
                        StandardOpenOption.READ,
                        LinkOption.NOFOLLOW_LINKS
                )
        );
    }

    @Override
    public void delete(String objectKey) throws IOException {
        Path file = resolveObject(objectKey);

        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(
                        file,
                        LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(
                    "The backup storage entry is not a regular file."
            );
        }

        // Already missing counts as deleted, making cleanup retryable.
        Files.deleteIfExists(file);
    }

    private Path resolveObject(String objectKey) throws IOException {
        validateObjectKey(objectKey);
        return storageDirectory().resolve(objectKey);
    }

    private Path storageDirectory() throws IOException {
        Path directory = settings.getLocalDirectory();

        Files.createDirectories(directory);

        if (Files.isSymbolicLink(directory)
                || !Files.isDirectory(
                        directory,
                        LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(
                    "The local backup directory is invalid."
            );
        }

        return directory.toRealPath();
    }

    private void validateObjectKey(String objectKey) throws IOException {
        if (objectKey == null
                || !objectKey.matches(
                        "cellbank-[a-zA-Z0-9-]{1,100}\\.dump")) {
            throw new IOException(
                    "The backup filename is invalid."
            );
        }
    }

    private String calculateSha256(Path file) throws IOException {
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
                Files.newInputStream(
                        file,
                        LinkOption.NOFOLLOW_LINKS),
                digest)) {

            input.transferTo(OutputStream.nullOutputStream());
        }

        return HexFormat.of().formatHex(digest.digest());
    }
}