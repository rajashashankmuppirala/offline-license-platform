package com.enterprise.licensing.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.licensing.model.LicenseOperation;
import com.enterprise.licensing.model.LicenseReasonCode;
import com.enterprise.licensing.storage.repository.SqliteAuditRepository;
import com.enterprise.licensing.storage.sqlite.SqliteDatabase;
import com.enterprise.licensing.storage.sqlite.SqliteImmediateTransactionRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicAuditTransactionTest {
    @TempDir Path temp;

    @Test
    void auditFailureRollsBackTheProtectedMutation() {
        try (var database = new SqliteDatabase(temp.resolve("atomic-audit.db"))) {
            var transactions = new SqliteImmediateTransactionRunner(database);
            var audit = new SqliteAuditRepository(database, transactions, new ObjectMapper());
            database.withConnection(connection -> {
                try (var statement = connection.createStatement()) {
                    statement.execute("CREATE TRIGGER reject_audit BEFORE INSERT ON audit_event "
                            + "BEGIN SELECT RAISE(ABORT, 'audit failure'); END");
                    return null;
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });

            assertThatThrownBy(() -> transactions.write(() -> {
                insertRevocation(database);
                audit.append(UUID.randomUUID(), Instant.parse("2026-01-01T00:00:00Z"), "C", "L", null,
                        LicenseOperation.ACTIVATION_IMPORT, "license", true,
                        LicenseReasonCode.LICENSE_VALID, Map.of());
                return null;
            })).hasMessageContaining("Audit persistence failure");

            assertThat(count(database, "local_revocation")).isZero();
            assertThat(count(database, "audit_event")).isZero();
        }
    }

    private static void insertRevocation(SqliteDatabase database) {
        database.withConnection(connection -> {
            try (var statement = connection.prepareStatement(
                    "INSERT INTO local_revocation(revocation_id,artifact_type,artifact_id,reason,effective_at,imported_at) VALUES(?,?,?,?,?,?)")) {
                statement.setString(1, "revocation");
                statement.setString(2, "TEST");
                statement.setString(3, "artifact");
                statement.setString(4, "test");
                statement.setString(5, "2026-01-01T00:00:00Z");
                statement.setString(6, "2026-01-01T00:00:00Z");
                statement.executeUpdate();
                return null;
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });
    }

    private static int count(SqliteDatabase database, String table) {
        return database.withConnection(connection -> {
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT count(*) FROM " + table)) {
                return result.getInt(1);
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });
    }
}
