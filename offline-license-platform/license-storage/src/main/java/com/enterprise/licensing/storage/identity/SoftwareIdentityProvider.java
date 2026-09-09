package com.enterprise.licensing.storage.identity;

import com.enterprise.licensing.crypto.Ed25519Keys;
import com.enterprise.licensing.engine.identity.IdentityMaterial;
import com.enterprise.licensing.engine.identity.IdentityProvider;
import com.enterprise.licensing.engine.ports.TimeSource;
import com.enterprise.licensing.model.EncodedPublicKey;
import com.enterprise.licensing.model.LicenseServerIdentity;
import com.enterprise.licensing.storage.sqlite.SqliteStorageException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyPair;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SoftwareIdentityProvider implements IdentityProvider {
    private static final Set<PosixFilePermission> OWNER_ONLY = Set.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
    private final Path identityFile;
    private final TimeSource timeSource;
    private final Map<String, String> metadata;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public SoftwareIdentityProvider(Path identityFile, TimeSource timeSource, Map<String, String> metadata) {
        this.identityFile = identityFile.toAbsolutePath().normalize();
        this.timeSource = timeSource;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    @Override
    public synchronized IdentityMaterial loadOrCreate() {
        return Files.exists(identityFile) ? load() : create();
    }

    private IdentityMaterial create() {
        try {
            Path parent = identityFile.getParent();
            if (parent == null) throw new IllegalArgumentException("Identity path needs a parent directory");
            Files.createDirectories(parent);
            KeyPair pair = Ed25519Keys.generate();
            PersistedIdentity stored = new PersistedIdentity(UUID.randomUUID(), "Ed25519",
                    Ed25519Keys.encodePublic(pair.getPublic()), Ed25519Keys.encodePrivate(pair.getPrivate()),
                    Ed25519Keys.fingerprint(pair.getPublic()), timeSource.now(), metadata);
            Path temporary = Files.createTempFile(parent, ".license-server-identity-", ".tmp");
            try {
                mapper.writeValue(temporary.toFile(), stored);
                restrict(temporary);
                try {
                    Files.move(temporary, identityFile, StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, identityFile);
                }
                restrict(identityFile);
            } finally {
                Files.deleteIfExists(temporary);
            }
            return toMaterial(stored);
        } catch (Exception e) {
            throw new SqliteStorageException("Unable to create License Server identity", e);
        }
    }

    private IdentityMaterial load() {
        try {
            if (!Files.isRegularFile(identityFile) || Files.size(identityFile) == 0) {
                throw new IllegalStateException("Identity file is missing or empty");
            }
            PersistedIdentity stored = mapper.readValue(identityFile.toFile(), PersistedIdentity.class);
            IdentityMaterial material = toMaterial(stored);
            String actual = Ed25519Keys.fingerprint(material.publicKey());
            if (!actual.equals(stored.publicKeyFingerprint())) {
                throw new IllegalStateException("Identity public-key fingerprint mismatch");
            }
            byte[] challenge = "identity-self-check".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if (!Ed25519Keys.verify(material.publicKey(), challenge, Ed25519Keys.sign(material.privateKey(), challenge))) {
                throw new IllegalStateException("Identity public/private key mismatch");
            }
            restrict(identityFile);
            return material;
        } catch (Exception e) {
            throw new SqliteStorageException("Unable to load License Server identity", e);
        }
    }

    private IdentityMaterial toMaterial(PersistedIdentity stored) {
        if (stored.licenseServerId() == null || !"Ed25519".equals(stored.algorithm())) {
            throw new IllegalStateException("Unsupported identity material");
        }
        var publicKey = Ed25519Keys.decodePublic(stored.publicKey());
        var privateKey = Ed25519Keys.decodePrivate(stored.privateKey());
        var publicIdentity = new LicenseServerIdentity(stored.licenseServerId(),
                new EncodedPublicKey(stored.algorithm(), "X.509", stored.publicKey()), stored.publicKeyFingerprint(),
                stored.createdAt(), stored.installationMetadata());
        return new IdentityMaterial(publicIdentity, publicKey, privateKey);
    }

    private static void restrict(Path file) throws Exception {
        try { Files.setPosixFilePermissions(file, OWNER_ONLY); }
        catch (UnsupportedOperationException ignored) { /* Platform ACLs apply on non-POSIX filesystems. */ }
    }

    private record PersistedIdentity(UUID licenseServerId, String algorithm, String publicKey, String privateKey,
                                     String publicKeyFingerprint, Instant createdAt,
                                     Map<String, String> installationMetadata) {}
}
