package com.enterprise.licensing.storage.sqlite;

import java.nio.file.Path;

public record SqliteStorageConfiguration(Path databaseFile, Path identityDirectory, Path checkpointFile) {
    public SqliteStorageConfiguration {
        if (databaseFile == null || identityDirectory == null || checkpointFile == null) throw new IllegalArgumentException("storage paths are required");
    }
}
