package com.enterprise.licensing.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

public final class InstanceRequestCanonicalizer {
    public byte[] canonicalize(String method, String path, String timestamp, String nonce, byte[] body) {
        try {
            String digest = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(body == null ? new byte[0] : body));
            return ("INSTANCE-REQUEST-V1\n" + method.toUpperCase(java.util.Locale.ROOT) + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + digest).getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new CryptoException("Cannot canonicalize instance request", e);
        }
    }
}
