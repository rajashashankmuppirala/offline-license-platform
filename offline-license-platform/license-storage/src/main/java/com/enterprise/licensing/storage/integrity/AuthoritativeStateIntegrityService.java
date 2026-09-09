package com.enterprise.licensing.storage.integrity;

import com.enterprise.licensing.crypto.CanonicalJson;
import com.enterprise.licensing.engine.identity.LicenseServerIdentityService;
import com.enterprise.licensing.engine.validation.AuthoritativeStateVerifier;
import com.enterprise.licensing.model.LicenseReasonCode;
import com.enterprise.licensing.storage.sqlite.SqliteDatabase;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Detects ordinary out-of-band edits to all authoritative licensing tables. */
public final class AuthoritativeStateIntegrityService implements AuthoritativeStateVerifier {
    public static final List<String> PROTECTED_TABLES = List.of(
            "license_server_identity", "commercial_license", "activation_certificate",
            "local_revocation", "product_instance", "named_user_assignment", "user_lease",
            "registered_agent", "agent_lease", "meter_usage", "usage_consumption",
            "trusted_time_state", "cached_entitlement_issue", "request_nonce", "audit_event");

    private final SqliteDatabase database;
    private final LicenseServerIdentityService identity;
    private final Path checkpointFile;
    private final ObjectMapper mapper;
    private final CanonicalJson canonical;
    private final ThreadLocal<Boolean> verifiedRequest = ThreadLocal.withInitial(() -> false);

    public AuthoritativeStateIntegrityService(SqliteDatabase database,
                                              LicenseServerIdentityService identity,
                                              Path checkpointFile,
                                              ObjectMapper mapper) {
        this.database = database;
        this.identity = identity;
        this.checkpointFile = checkpointFile.toAbsolutePath().normalize();
        this.mapper = mapper;
        this.canonical = new CanonicalJson(mapper);
    }

    @Override public synchronized LicenseReasonCode verifyOrInitialize() {
        if (verifiedRequest.get()) return LicenseReasonCode.LICENSE_VALID;
        if (!Files.exists(checkpointFile)) {
            if (hasActivatedState()) return LicenseReasonCode.STATE_INTEGRITY_FAILURE;
            seal();
            return LicenseReasonCode.LICENSE_VALID;
        }
        final Checkpoint checkpoint;
        try {
            checkpoint = mapper.readValue(checkpointFile.toFile(), Checkpoint.class);
        } catch (Exception exception) {
            return LicenseReasonCode.STATE_INTEGRITY_FAILURE;
        }
        var server = identity.publicIdentity();
        if (!server.licenseServerId().toString().equals(checkpoint.licenseServerId())) {
            return LicenseReasonCode.STATE_INTEGRITY_FAILURE;
        }
        try {
            byte[] signature = Base64.getUrlDecoder().decode(checkpoint.signature());
            if (!identity.verifiesInstallationProof(content(checkpoint.digest()), signature)) {
                return LicenseReasonCode.STATE_INTEGRITY_FAILURE;
            }
        } catch (RuntimeException exception) {
            return LicenseReasonCode.STATE_INTEGRITY_FAILURE;
        }
        return MessageDigest.isEqual(checkpoint.digest().getBytes(StandardCharsets.US_ASCII),
                digest().getBytes(StandardCharsets.US_ASCII))
                ? LicenseReasonCode.LICENSE_VALID : LicenseReasonCode.STATE_INTEGRITY_FAILURE;
    }

    public void beginVerifiedRequest() {
        if (verifiedRequest.get()) throw new IllegalStateException("Verified request scope is already active");
        verifiedRequest.set(true);
    }

    public void endVerifiedRequest() {
        verifiedRequest.remove();
    }

    public synchronized void seal() {
        try {
            Files.createDirectories(checkpointFile.getParent());
            String digest = digest();
            String signature = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(identity.signInstallationProof(content(digest)));
            var checkpoint = new Checkpoint(identity.publicIdentity().licenseServerId().toString(), digest, signature);
            Path temporary = Files.createTempFile(checkpointFile.getParent(), ".state-integrity-", ".tmp");
            try {
                mapper.writeValue(temporary.toFile(), checkpoint);
                try {
                    Files.move(temporary, checkpointFile, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException exception) {
                    Files.move(temporary, checkpointFile, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to seal authoritative licensing state", exception);
        }
    }

    private String digest() {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.canonicalize(snapshot())));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to digest authoritative licensing state", exception);
        }
    }

    private Map<String, Object> snapshot() {
        var snapshot = new LinkedHashMap<String, Object>();
        for (String table : PROTECTED_TABLES) snapshot.put(table, rows(table));
        return snapshot;
    }

    private List<Map<String, Object>> rows(String table) {
        return database.withConnection(connection -> {
            try (var statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT * FROM " + table + " ORDER BY rowid")) {
                var rows = new ArrayList<Map<String, Object>>();
                var metadata = result.getMetaData();
                while (result.next()) {
                    var row = new TreeMap<String, Object>();
                    for (int column = 1; column <= metadata.getColumnCount(); column++) {
                        row.put(metadata.getColumnName(column), result.getObject(column));
                    }
                    rows.add(row);
                }
                return List.copyOf(rows);
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to read protected table " + table, exception);
            }
        });
    }

    private boolean hasActivatedState() {
        return database.withConnection(connection -> {
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT EXISTS(SELECT 1 FROM commercial_license WHERE active=1)")) {
                return result.getInt(1) == 1;
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to inspect activation state", exception);
            }
        });
    }

    private byte[] content(String digest) {
        return canonical.canonicalize(Map.of(
                "format", "AUTHORITATIVE_STATE_V1",
                "licenseServerId", identity.publicIdentity().licenseServerId().toString(),
                "digest", digest));
    }

    private record Checkpoint(String licenseServerId, String digest, String signature) {}
}
