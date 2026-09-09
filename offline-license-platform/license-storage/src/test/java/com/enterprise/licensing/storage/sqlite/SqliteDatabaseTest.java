package com.enterprise.licensing.storage.sqlite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SqliteDatabaseTest {
    @Test
    void migratesCompleteSchemaAndCommitsImmediateWrites(@TempDir Path directory) {
        try (var database = new SqliteDatabase(directory.resolve("license.db"))) {
            var transaction = new SqliteImmediateTransactionRunner(database);
            transaction.write(() -> database.withConnection(connection -> {
                try (var statement = connection.prepareStatement("INSERT INTO local_revocation(revocation_id, artifact_type, artifact_id, reason, effective_at, imported_at) VALUES(?,?,?,?,?,?)")) {
                    statement.setString(1, "REV-1");
                    statement.setString(2, "COMMERCIAL_LICENSE");
                    statement.setString(3, "LIC-1");
                    statement.setString(4, "test");
                    statement.setString(5, "2026-01-01T00:00:00Z");
                    statement.setString(6, "2026-01-01T00:00:00Z");
                    statement.executeUpdate();
                    return null;
                } catch (java.sql.SQLException e) {
                    throw new SqliteStorageException("insert failed", e);
                }
            }));
            long count = database.withConnection(connection -> {
                try (var result = connection.createStatement().executeQuery("SELECT count(*) FROM local_revocation")) {
                    return result.next() ? result.getLong(1) : 0L;
                } catch (java.sql.SQLException e) {
                    throw new SqliteStorageException("query failed", e);
                }
            });
            assertThat(count).isEqualTo(1);
        }
    }
}
