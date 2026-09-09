package com.enterprise.licensing.storage;

import static org.assertj.core.api.Assertions.*;
import com.enterprise.licensing.crypto.Ed25519Keys;
import com.enterprise.licensing.model.*;
import com.enterprise.licensing.storage.operations.SqliteBackupService;
import com.enterprise.licensing.storage.sqlite.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteBackupServiceTest {
    @TempDir Path temp;

    @Test void onlineBackupAndRestoreAreIdentityBoundNonOverwritingAndFailureAtomic()throws Exception {
        var database=new SqliteDatabase(temp.resolve("live.db"));var transactions=new SqliteImmediateTransactionRunner(database);
        var keys=Ed25519Keys.generate();var identity=identity(keys);insertIdentity(database,identity);
        var service=new SqliteBackupService(database,transactions);Path backup=service.backup(temp.resolve("backup/license.db"));
        assertThat(service.validateForOriginalIdentity(backup,identity)).isEqualTo(LicenseReasonCode.LICENSE_VALID);
        assertThat(new String(Files.readAllBytes(backup),java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain(Ed25519Keys.encodePrivate(keys.getPrivate()));

        assertThatThrownBy(()->service.backup(backup)).isInstanceOf(IllegalArgumentException.class);
        var other=identity(Ed25519Keys.generate());
        assertThat(service.validateForOriginalIdentity(backup,other)).isEqualTo(LicenseReasonCode.LICENSE_SERVER_IDENTITY_MISMATCH);
        Path rejected=temp.resolve("restore/rejected.db");
        assertThatThrownBy(()->service.stageValidatedRestore(backup,rejected,other)).isInstanceOf(SqliteStorageException.class);
        assertThat(rejected).doesNotExist();assertThat(rejected.resolveSibling("rejected.db.partial")).doesNotExist();

        Path restored=temp.resolve("restore/restored.db");
        assertThat(service.stageValidatedRestore(backup,restored,identity)).isEqualTo(restored.toAbsolutePath());
        assertThat(service.validateForOriginalIdentity(restored,identity)).isEqualTo(LicenseReasonCode.LICENSE_VALID);
        byte[] original=Files.readAllBytes(restored);
        assertThatThrownBy(()->service.stageValidatedRestore(backup,restored,identity)).isInstanceOf(IllegalArgumentException.class);
        assertThat(Files.readAllBytes(restored)).isEqualTo(original);

        Path corrupt=temp.resolve("corrupt.db");Files.writeString(corrupt,"not sqlite");Path corruptTarget=temp.resolve("restore/corrupt.db");
        assertThat(service.validateForOriginalIdentity(corrupt,identity)).isEqualTo(LicenseReasonCode.STATE_INTEGRITY_FAILURE);
        assertThatThrownBy(()->service.stageValidatedRestore(corrupt,corruptTarget,identity)).isInstanceOf(SqliteStorageException.class);
        assertThat(corruptTarget).doesNotExist();assertThat(corruptTarget.resolveSibling("corrupt.db.partial")).doesNotExist();
    }

    private static LicenseServerIdentity identity(java.security.KeyPair keys){return new LicenseServerIdentity(UUID.randomUUID(),new EncodedPublicKey("Ed25519","X.509",Ed25519Keys.encodePublic(keys.getPublic())),Ed25519Keys.fingerprint(keys.getPublic()),Instant.parse("2026-01-01T00:00:00Z"),Map.of());}
    private static void insertIdentity(SqliteDatabase database,LicenseServerIdentity identity){database.withConnection(connection->{try(var statement=connection.prepareStatement("INSERT INTO license_server_identity(singleton_id,server_id,public_key,public_key_fingerprint,created_at,metadata_json)VALUES(1,?,?,?,?,?)")){statement.setString(1,identity.licenseServerId().toString());statement.setString(2,identity.publicKey().encoded());statement.setString(3,identity.publicKeyFingerprint());statement.setString(4,identity.createdAt().toString());statement.setString(5,"{}");statement.executeUpdate();return null;}catch(Exception exception){throw new RuntimeException(exception);}});}
}
