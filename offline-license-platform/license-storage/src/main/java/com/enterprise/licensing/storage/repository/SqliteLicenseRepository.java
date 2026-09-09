package com.enterprise.licensing.storage.repository;

import com.enterprise.licensing.crypto.CanonicalJson;
import com.enterprise.licensing.crypto.EnvelopeJsonCodec;
import com.enterprise.licensing.engine.activation.ActiveLicenseState;
import com.enterprise.licensing.engine.activation.LicenseRepository;
import com.enterprise.licensing.engine.ports.TransactionRunner;
import com.enterprise.licensing.model.ActivationCertificate;
import com.enterprise.licensing.model.CommercialLicense;
import com.enterprise.licensing.model.LicenseServerIdentity;
import com.enterprise.licensing.model.SignedEnvelope;
import com.enterprise.licensing.storage.sqlite.SqliteDatabase;
import com.enterprise.licensing.storage.sqlite.SqliteStorageException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

public final class SqliteLicenseRepository implements LicenseRepository {
    private final SqliteDatabase database;
    private final TransactionRunner transactions;
    private final ObjectMapper mapper;
    private final EnvelopeJsonCodec codec;

    public SqliteLicenseRepository(SqliteDatabase database, TransactionRunner transactions, ObjectMapper mapper) {
        this.database = database; this.transactions = transactions; this.mapper = mapper; this.codec = new EnvelopeJsonCodec(mapper);
    }

    @Override
    public void activate(LicenseServerIdentity identity, CommercialLicense license, SignedEnvelope licenseEnvelope,
                         ActivationCertificate certificate, SignedEnvelope activationEnvelope, Instant importedAt) {
        transactions.write(() -> {
            upsertIdentity(identity);
            execute("UPDATE commercial_license SET active=0 WHERE active=1");
            execute("INSERT INTO commercial_license(license_id,customer_id,envelope_json,payload_hash,valid_from,valid_until,grace_until,imported_at,active) VALUES(?,?,?,?,?,?,?,?,1) " +
                            "ON CONFLICT(license_id) DO UPDATE SET customer_id=excluded.customer_id,envelope_json=excluded.envelope_json,payload_hash=excluded.payload_hash,valid_from=excluded.valid_from,valid_until=excluded.valid_until,grace_until=excluded.grace_until,imported_at=excluded.imported_at,active=1",
                    license.licenseId(), license.customer().customerId(), codec.write(licenseEnvelope), sha256(licenseEnvelope.payload()),
                    license.subscription().validFrom().toString(), license.subscription().validUntil().toString(),
                    license.subscription().graceUntil().toString(), importedAt.toString());
            execute("UPDATE activation_certificate SET active=0 WHERE active=1");
            execute("INSERT INTO activation_certificate(activation_id,license_id,server_id,public_key_fingerprint,envelope_json,issued_at,active) VALUES(?,?,?,?,?,?,1) " +
                            "ON CONFLICT(activation_id) DO UPDATE SET license_id=excluded.license_id,server_id=excluded.server_id,public_key_fingerprint=excluded.public_key_fingerprint,envelope_json=excluded.envelope_json,issued_at=excluded.issued_at,active=1",
                    certificate.activationId(), certificate.licenseId(), certificate.licenseServerId().toString(), certificate.publicKeyFingerprint(),
                    codec.write(activationEnvelope), certificate.issuedAt().toString());
            return null;
        });
    }

    @Override public Optional<ActiveLicenseState> active() {
        return transactions.read(() -> database.withConnection(connection -> {
            try (var statement = connection.prepareStatement("SELECT l.envelope_json,a.envelope_json,l.imported_at FROM commercial_license l CROSS JOIN activation_certificate a WHERE l.active=1 AND a.active=1")) {
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) return Optional.empty();
                    var licenseEnvelope = codec.read(result.getString(1)); var activationEnvelope = codec.read(result.getString(2));
                    return Optional.of(new ActiveLicenseState(codec.payload(licenseEnvelope, CommercialLicense.class), licenseEnvelope,
                            codec.payload(activationEnvelope, ActivationCertificate.class), activationEnvelope, Instant.parse(result.getString(3))));
                }
            } catch (Exception e) { throw new SqliteStorageException("Unable to read active license", e); }
        }));
    }

    @Override public boolean isRevoked(String artifactType, String artifactId, Instant at) {
        return transactions.read(() -> database.withConnection(connection -> {
            try (var statement = connection.prepareStatement("SELECT 1 FROM local_revocation WHERE artifact_type=? AND artifact_id=? AND effective_at<=?")) {
                statement.setString(1, artifactType); statement.setString(2, artifactId); statement.setString(3, at.toString());
                try (var result = statement.executeQuery()) { return result.next(); }
            } catch (Exception e) { throw new SqliteStorageException("Unable to read local revocation", e); }
        }));
    }

    private void upsertIdentity(LicenseServerIdentity identity) {
        try {
            execute("INSERT INTO license_server_identity(singleton_id,server_id,public_key,public_key_fingerprint,created_at,metadata_json) VALUES(1,?,?,?,?,?) " +
                            "ON CONFLICT(singleton_id) DO UPDATE SET server_id=excluded.server_id,public_key=excluded.public_key,public_key_fingerprint=excluded.public_key_fingerprint,created_at=excluded.created_at,metadata_json=excluded.metadata_json",
                    identity.licenseServerId().toString(), identity.publicKey().encoded(), identity.publicKeyFingerprint(), identity.createdAt().toString(), mapper.writeValueAsString(identity.installationMetadata()));
        } catch (Exception e) { throw new SqliteStorageException("Unable to persist identity projection", e); }
    }

    private void execute(String sql, Object... values) {
        database.withConnection(connection -> {
            try (var statement = connection.prepareStatement(sql)) {
                for (int index = 0; index < values.length; index++) statement.setObject(index + 1, values[index]);
                statement.executeUpdate(); return null;
            } catch (Exception e) { throw new SqliteStorageException("SQLite license write failed", e); }
        });
    }

    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
