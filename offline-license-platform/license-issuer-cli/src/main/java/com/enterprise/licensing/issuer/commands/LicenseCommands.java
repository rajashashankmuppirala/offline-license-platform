package com.enterprise.licensing.issuer.commands;

import com.enterprise.licensing.crypto.CanonicalJson;
import com.enterprise.licensing.crypto.Ed25519Keys;
import com.enterprise.licensing.crypto.EnvelopeJsonCodec;
import com.enterprise.licensing.crypto.runtime.CommercialLicenseVerifier;
import com.enterprise.licensing.crypto.runtime.RuntimeEnvelopeVerifier;
import com.enterprise.licensing.crypto.vendor.CommercialLicenseSigner;
import com.enterprise.licensing.crypto.vendor.VendorEnvelopeSigner;
import com.enterprise.licensing.model.CommercialLicense;
import com.enterprise.licensing.model.CommercialLicenseJsonCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyPair;
import java.util.Set;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

public final class LicenseCommands {
    private LicenseCommands() {
    }

    static ObjectMapper mapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    static void write(Path path, Object value) throws Exception {
        Path parent = path.toAbsolutePath().normalize().getParent();
        if (parent != null) Files.createDirectories(parent);
        mapper().writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value);
    }

    static KeyPair keys(Path privateKey, Path publicKey) throws Exception {
        return new KeyPair(Ed25519Keys.decodePublic(Files.readString(publicKey).trim()),
                Ed25519Keys.decodePrivate(Files.readString(privateKey).trim()));
    }

    @Command(name = "keygen", description = "Generate a new vendor Ed25519 key pair")
    public static final class Keygen implements Callable<Integer> {
        @Option(names = "--private-key", required = true)
        Path privateKey;
        @Option(names = "--public-key", required = true)
        Path publicKey;

        @Override
        public Integer call() throws Exception {
            if (Files.exists(privateKey) || Files.exists(publicKey))
                throw new IllegalArgumentException("Refusing to overwrite a key file");
            var pair = Ed25519Keys.generate();
            Files.createDirectories(privateKey.toAbsolutePath().getParent());
            Files.writeString(privateKey, Ed25519Keys.encodePrivate(pair.getPrivate()));
            try {
                Files.setPosixFilePermissions(privateKey, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            } catch (UnsupportedOperationException ignored) {
            }
            Files.writeString(publicKey, Ed25519Keys.encodePublic(pair.getPublic()));
            System.out.println("{\"result\":\"KEY_PAIR_GENERATED\",\"keyId\":\"" + Ed25519Keys.fingerprint(pair.getPublic()) + "\"}");
            return 0;
        }
    }

    @Command(name = "issue-license", description = "Sign a commercial license")
    public static final class Issue implements Callable<Integer> {
        @Option(names = "--input", required = true)
        Path input;
        @Option(names = "--private-key", required = true)
        Path privateKey;
        @Option(names = "--public-key", required = true)
        Path publicKey;
        @Option(names = "--output", required = true)
        Path output;

        @Override
        public Integer call() throws Exception {
            var mapper = mapper();
            var license = new CommercialLicenseJsonCodec(mapper).readVersioned(Files.readAllBytes(input));
            var signer = new CommercialLicenseSigner(new VendorEnvelopeSigner(new CanonicalJson(mapper)));
            write(output, signer.sign(license, keys(privateKey, publicKey)));
            System.out.println("{\"result\":\"LICENSE_ISSUED\",\"licenseId\":\"" + license.licenseId() + "\"}");
            return 0;
        }
    }

    @Command(name = "inspect-license", description = "Inspect a signed commercial license without trusting it")
    public static final class Inspect implements Callable<Integer> {
        @Option(names = "--license", required = true)
        Path license;

        @Override
        public Integer call() throws Exception {
            var mapper = mapper();
            var codec = new EnvelopeJsonCodec(mapper);
            var envelope = codec.read(Files.readString(license));
            var payload = codec.payload(envelope, CommercialLicense.class);
            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload));
            return 0;
        }
    }

    @Command(name = "verify-license", description = "Verify a signed commercial license")
    public static final class Verify implements Callable<Integer> {
        @Option(names = "--license", required = true)
        Path license;
        @Option(names = "--public-key", required = true)
        Path publicKey;

        @Override
        public Integer call() throws Exception {
            var mapper = mapper();
            var canonical = new CanonicalJson(mapper);
            var codec = new EnvelopeJsonCodec(mapper);
            var verifier = new CommercialLicenseVerifier(new RuntimeEnvelopeVerifier(canonical, Set.of("1.0")), codec);
            var result = verifier.verify(codec.read(Files.readString(license)), Ed25519Keys.decodePublic(Files.readString(publicKey).trim()));
            System.out.println("{\"valid\":" + result.valid() + ",\"reasonCode\":\"" + result.reasonCode() + "\"}");
            return result.valid() ? 0 : 3;
        }
    }
}
