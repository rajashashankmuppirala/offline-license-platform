package com.enterprise.licensing.crypto;

import com.enterprise.licensing.model.ArtifactType;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public final class EnvelopeSupport {
    public static final String ENVELOPE_VERSION = "1";
    public static final String ALGORITHM = "Ed25519";
    private static final byte[] DOMAIN = "OFFLINE-LICENSE-SIGNED-ENVELOPE-V1".getBytes(StandardCharsets.US_ASCII);

    private EnvelopeSupport() {}

    public static byte[] signatureInput(ArtifactType type, String schemaVersion, String algorithm,
                                        String keyId, byte[] canonicalPayload) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            writePart(out, DOMAIN);
            writePart(out, type.name().getBytes(StandardCharsets.US_ASCII));
            writePart(out, schemaVersion.getBytes(StandardCharsets.UTF_8));
            writePart(out, algorithm.getBytes(StandardCharsets.US_ASCII));
            writePart(out, keyId.getBytes(StandardCharsets.US_ASCII));
            writePart(out, canonicalPayload);
            return out.toByteArray();
        } catch (Exception e) { throw new CryptoException("Unable to build signature input", e); }
    }

    private static void writePart(ByteArrayOutputStream out, byte[] value) {
        out.writeBytes(java.nio.ByteBuffer.allocate(Integer.BYTES).putInt(value.length).array());
        out.writeBytes(value);
    }
}
