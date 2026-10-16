package com.cellbank.backup;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

import com.cellbank.backup.BackupRun.StorageProvider;

public interface BackupStorage {

    StorageProvider getProvider();

    StoredBackup store(
            String objectKey,
            Path source,
            long expectedSize,
            String expectedSha256) throws IOException;

    InputStream open(String objectKey) throws IOException;

    void delete(String objectKey) throws IOException;

    record StoredBackup(
            String objectKey,
            long sizeBytes,
            String sha256) {
    }
}
