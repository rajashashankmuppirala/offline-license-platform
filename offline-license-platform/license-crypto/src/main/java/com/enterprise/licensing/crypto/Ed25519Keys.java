package com.enterprise.licensing.crypto;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;

public final class Ed25519Keys {
    private Ed25519Keys() {
    }

    public static KeyPair generate() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (Exception e) {
            throw new CryptoException("Ed25519 is unavailable", e);
        }
    }

    public static byte[] sign(PrivateKey key, byte[] content) {
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(key);
            signature.update(content);
            return signature.sign();
        } catch (Exception e) {
            throw new CryptoException("Unable to sign content", e);
        }
    }

    public static boolean verify(PublicKey key, byte[] content, byte[] signatureBytes) {
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initVerify(key);
            signature.update(content);
            return signature.verify(signatureBytes);
        } catch (Exception e) {
            return false;
        }
    }

    public static String encodePublic(PublicKey key) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(key.getEncoded());
    }

    public static String encodePrivate(PrivateKey key) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(key.getEncoded());
    }

    public static PublicKey decodePublic(String encoded) {
        try {
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getUrlDecoder().decode(encoded)));
        } catch (Exception e) {
            throw new CryptoException("Invalid Ed25519 public key", e);
        }
    }

    public static PrivateKey decodePrivate(String encoded) {
        try {
            return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(Base64.getUrlDecoder().decode(encoded)));
        } catch (Exception e) {
            throw new CryptoException("Invalid Ed25519 private key", e);
        }
    }

    public static String fingerprint(PublicKey key) {
        try {
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(key.getEncoded()));
        } catch (Exception e) {
            throw new CryptoException("Unable to fingerprint public key", e);
        }
    }

    public static void writePrivate(Path path, PrivateKey key) {
        try {
            Files.writeString(path, encodePrivate(key));
        } catch (Exception e) {
            throw new CryptoException("Unable to write private key", e);
        }
    }
}
