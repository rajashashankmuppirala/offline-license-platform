package com.enterprise.licensing.client.identity;

import com.enterprise.licensing.crypto.Ed25519Keys;
import com.enterprise.licensing.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.security.*;
import java.time.Instant;
import java.util.*;

public final class ProductInstanceIdentityStore {
    private final Path file;
    private final ObjectMapper mapper;

    public ProductInstanceIdentityStore(Path file, ObjectMapper mapper) {
        this.file = file.toAbsolutePath().normalize();
        this.mapper = mapper;
    }

    private static Material material(Persisted p) {
        var pub = Ed25519Keys.decodePublic(p.publicKey());
        return new Material(new ProductInstanceIdentity(p.instanceId(), new EncodedPublicKey("Ed25519", "X.509", p.publicKey()), p.fingerprint(), p.createdAt()), pub, Ed25519Keys.decodePrivate(p.privateKey()));
    }

    private static void restrict(Path p) throws Exception {
        try {
            Files.setPosixFilePermissions(p, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
        }
    }

    public synchronized Material loadOrCreate() {
        try {
            if (Files.exists(file)) return load();
            Files.createDirectories(file.getParent());
            var key = Ed25519Keys.generate();
            var persisted = new Persisted(UUID.randomUUID().toString(), Ed25519Keys.encodePublic(key.getPublic()), Ed25519Keys.encodePrivate(key.getPrivate()), Ed25519Keys.fingerprint(key.getPublic()), Instant.now());
            var temporary = Files.createTempFile(file.getParent(), ".instance-identity-", ".tmp");
            try {
                mapper.writeValue(temporary.toFile(), persisted);
                restrict(temporary);
                try {
                    Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temporary, file);
                }
                restrict(file);
            } finally {
                Files.deleteIfExists(temporary);
            }
            return material(persisted);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create product instance identity", e);
        }
    }

    private Material load() {
        try {
            var p = mapper.readValue(file.toFile(), Persisted.class);
            var m = material(p);
            if (!Ed25519Keys.fingerprint(m.publicKey()).equals(p.fingerprint()))
                throw new IllegalStateException("fingerprint mismatch");
            byte[] challenge = "self-test".getBytes();
            if (!Ed25519Keys.verify(m.publicKey(), challenge, Ed25519Keys.sign(m.privateKey(), challenge)))
                throw new IllegalStateException("key mismatch");
            restrict(file);
            return m;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load product instance identity", e);
        }
    }

    public record Material(ProductInstanceIdentity identity, PublicKey publicKey, PrivateKey privateKey) {
    }

    private record Persisted(String instanceId, String publicKey, String privateKey, String fingerprint,
                             Instant createdAt) {
    }
}
