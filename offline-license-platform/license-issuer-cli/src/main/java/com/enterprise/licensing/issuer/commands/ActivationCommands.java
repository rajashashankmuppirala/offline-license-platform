package com.enterprise.licensing.issuer.commands;

import com.enterprise.licensing.crypto.CanonicalJson;
import com.enterprise.licensing.crypto.EnvelopeJsonCodec;
import com.enterprise.licensing.crypto.runtime.ActivationRequestProof;
import com.enterprise.licensing.crypto.runtime.CommercialLicenseVerifier;
import com.enterprise.licensing.crypto.runtime.RuntimeEnvelopeVerifier;
import com.enterprise.licensing.crypto.vendor.ActivationSigner;
import com.enterprise.licensing.crypto.vendor.VendorEnvelopeSigner;
import com.enterprise.licensing.model.ActivationCertificate;
import com.enterprise.licensing.model.ActivationRequest;
import com.enterprise.licensing.model.SignedEnvelope;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

public final class ActivationCommands {
    private ActivationCommands() {}

    @Command(name = "inspect-activation-request", description = "Inspect and verify an installation activation request")
    public static final class InspectRequest implements Callable<Integer> {
        @Option(names = "--request", required = true) Path request;
        @Override public Integer call() throws Exception {
            var mapper = LicenseCommands.mapper();
            var value = mapper.readValue(request.toFile(), ActivationRequest.class);
            boolean valid = new ActivationRequestProof(new CanonicalJson(mapper)).verify(value);
            System.out.println("{\"validProof\":" + valid + ",\"licenseServerId\":\"" + value.licenseServerId() + "\"}");
            return valid ? 0 : 3;
        }
    }

    @Command(name = "activate-license-server", description = "Issue an activation certificate for a verified request")
    public static final class Activate implements Callable<Integer> {
        @Option(names = "--request", required = true) Path request;
        @Option(names = "--license", required = true) Path license;
        @Option(names = "--private-key", required = true) Path privateKey;
        @Option(names = "--public-key", required = true) Path publicKey;
        @Option(names = "--output", required = true) Path output;
        @Override public Integer call() throws Exception {
            var mapper = LicenseCommands.mapper(); var canonical = new CanonicalJson(mapper);
            var activationRequest = mapper.readValue(request.toFile(), ActivationRequest.class);
            if (!new ActivationRequestProof(canonical).verify(activationRequest)) {
                System.out.println("{\"result\":\"ACTIVATION_REQUEST_PROOF_INVALID\"}"); return 3;
            }
            var codec = new EnvelopeJsonCodec(mapper);
            SignedEnvelope licenseEnvelope = codec.read(Files.readString(license));
            var licenseResult = new CommercialLicenseVerifier(new RuntimeEnvelopeVerifier(canonical, Set.of("1.0")), codec)
                    .verify(licenseEnvelope, com.enterprise.licensing.crypto.Ed25519Keys.decodePublic(Files.readString(publicKey).trim()));
            if (!licenseResult.valid()) {
                System.out.println("{\"result\":\"" + licenseResult.reasonCode() + "\"}"); return 3;
            }
            var commercial = licenseResult.license();
            if (!commercial.licenseId().equals(activationRequest.licenseId()) ||
                    !commercial.customer().customerId().equals(activationRequest.customerId())) {
                System.out.println("{\"result\":\"ACTIVATION_BINDING_INVALID\"}"); return 3;
            }
            var certificate = new ActivationCertificate("1.0", UUID.randomUUID().toString(), commercial.licenseId(),
                    commercial.customer().customerId(), activationRequest.licenseServerId(),
                    activationRequest.publicKeyFingerprint(), commercial.activation(), Instant.now());
            var signer = new ActivationSigner(new VendorEnvelopeSigner(canonical));
            LicenseCommands.write(output, signer.sign(certificate, LicenseCommands.keys(privateKey, publicKey)));
            System.out.println("{\"result\":\"LICENSE_SERVER_ACTIVATED\",\"activationId\":\"" + certificate.activationId() + "\"}");
            return 0;
        }
    }

    @Command(name = "inspect-activation-certificate", description = "Inspect an activation certificate without trusting it")
    public static final class InspectCertificate implements Callable<Integer> {
        @Option(names = "--certificate", required = true) Path certificate;
        @Override public Integer call() throws Exception {
            var mapper = LicenseCommands.mapper(); var codec = new EnvelopeJsonCodec(mapper);
            var value = codec.payload(codec.read(Files.readString(certificate)), ActivationCertificate.class);
            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value));
            return 0;
        }
    }
}
