package com.enterprise.licensing.client.cache;

import com.enterprise.licensing.crypto.EnvelopeJsonCodec;
import com.enterprise.licensing.model.SignedEnvelope;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Optional;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class FileEntitlementCache {
    private static final byte[] MAGIC = "OLC1".getBytes(StandardCharsets.US_ASCII);
    private static final int NONCE_BYTES = 12;
    private static final Set<PosixFilePermission> OWNER_ONLY = Set.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    private final Path file;
    private final EnvelopeJsonCodec codec;
    private final SecretKeySpec encryptionKey;
    private final SecureRandom random = new SecureRandom();

    public FileEntitlementCache(Path file, EnvelopeJsonCodec codec, byte[] installationKeyMaterial) {
        this.file = file.toAbsolutePath().normalize();
        this.codec = codec;
        if (installationKeyMaterial == null || installationKeyMaterial.length == 0) {
            throw new IllegalArgumentException("Installation key material is required");
        }
        try {
            this.encryptionKey = new SecretKeySpec(
                    MessageDigest.getInstance("SHA-256").digest(installationKeyMaterial.clone()), "AES");
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to derive cache encryption key", exception);
        }
    }

    private static void restrict(Path path) throws Exception {
        try {
            Files.setPosixFilePermissions(path, OWNER_ONLY);
        } catch (UnsupportedOperationException ignored) {
            // The platform does not expose POSIX permissions.
        }
    }

    public synchronized void save(SignedEnvelope envelope) {
        try {
            Files.createDirectories(file.getParent());
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(128, nonce));
            byte[] ciphertext = cipher.doFinal(codec.write(envelope).getBytes(StandardCharsets.UTF_8));
            byte[] stored = ByteBuffer.allocate(MAGIC.length + nonce.length + ciphertext.length)
                    .put(MAGIC).put(nonce).put(ciphertext).array();

            Path temporary = Files.createTempFile(file.getParent(), ".entitlement-", ".tmp");
            try {
                Files.write(temporary, stored);
                restrict(temporary);
                try {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                }
                restrict(file);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to persist cached entitlement", exception);
        }
    }

    public synchronized Optional<SignedEnvelope> load() {
        try {
            if (!Files.exists(file)) return Optional.empty();
            ByteBuffer stored = ByteBuffer.wrap(Files.readAllBytes(file));
            byte[] magic = new byte[MAGIC.length];
            stored.get(magic);
            if (!MessageDigest.isEqual(MAGIC, magic)) return Optional.empty();
            byte[] nonce = new byte[NONCE_BYTES];
            stored.get(nonce);
            byte[] ciphertext = new byte[stored.remaining()];
            stored.get(ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(128, nonce));
            return Optional.of(codec.read(new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}
