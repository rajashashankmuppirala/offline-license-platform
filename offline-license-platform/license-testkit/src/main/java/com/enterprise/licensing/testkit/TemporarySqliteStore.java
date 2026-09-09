package com.enterprise.licensing.testkit;

import com.enterprise.licensing.storage.sqlite.SqliteDatabase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TemporarySqliteStore implements AutoCloseable {
    private final Path directory;
    private final SqliteDatabase database;

    public TemporarySqliteStore() {
        try {
            directory = Files.createTempDirectory("offline-license-test-");
            database = new SqliteDatabase(directory.resolve("license.db"));
        } catch (IOException e) { throw new IllegalStateException("Unable to create temporary store", e); }
    }

    public SqliteDatabase database() { return database; }
    public Path directory() { return directory; }

    @Override public void close() { database.close(); }
}
