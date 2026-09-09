package com.enterprise.licensing.issuer;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.licensing.testkit.LicenseFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LicenseIssuerCliTest {
    @TempDir
    Path temp;

    private static ObjectMapper mapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Test
    void keygenIssueInspectAndVerifyHaveStableExitCodesAndJsonOutput() throws Exception {
        Path privateKey = temp.resolve("vendor.private");
        Path publicKey = temp.resolve("vendor.public");
        Path input = temp.resolve("license-input.json");
        Path signed = temp.resolve("license.signed.json");
        mapper().writeValue(input.toFile(), LicenseFixtures.aInc());

        var original = System.out;
        var bytes = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(bytes));
            assertThat(LicenseIssuerCli.execute("keygen", "--private-key", privateKey.toString(), "--public-key", publicKey.toString())).isZero();
            assertThat(LicenseIssuerCli.execute("issue-license", "--input", input.toString(), "--private-key", privateKey.toString(), "--public-key", publicKey.toString(), "--output", signed.toString())).isZero();
            assertThat(LicenseIssuerCli.execute("verify-license", "--license", signed.toString(), "--public-key", publicKey.toString())).isZero();
            assertThat(LicenseIssuerCli.execute("inspect-license", "--license", signed.toString())).isZero();
        } finally {
            System.setOut(original);
        }

        assertThat(Files.readString(privateKey)).doesNotContain("BEGIN");
        assertThat(Files.readString(signed)).contains("COMMERCIAL_LICENSE");
        assertThat(bytes.toString()).contains("KEY_PAIR_GENERATED", "LICENSE_ISSUED", "LICENSE_VALID", "LIC-AINC-2026-001");
    }

    @Test
    void tamperedEnvelopeReturnsVerificationFailure() throws Exception {
        Path privateKey = temp.resolve("vendor.private");
        Path publicKey = temp.resolve("vendor.public");
        Path input = temp.resolve("input.json");
        Path signed = temp.resolve("signed.json");
        mapper().writeValue(input.toFile(), LicenseFixtures.aInc());
        assertThat(LicenseIssuerCli.execute("keygen", "--private-key", privateKey.toString(), "--public-key", publicKey.toString())).isZero();
        assertThat(LicenseIssuerCli.execute("issue-license", "--input", input.toString(), "--private-key", privateKey.toString(), "--public-key", publicKey.toString(), "--output", signed.toString())).isZero();
        var mapper = mapper();
        var tree = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(signed.toFile());
        String signature = tree.get("signature").asText();
        tree.put("signature", (signature.charAt(0) == 'A' ? "B" : "A") + signature.substring(1));
        mapper.writeValue(signed.toFile(), tree);
        assertThat(LicenseIssuerCli.execute("verify-license", "--license", signed.toString(), "--public-key", publicKey.toString())).isEqualTo(3);
    }

    @Test
    void issueRejectsUnknownFieldsBeforeCreatingSignedOutput() throws Exception {
        Path privateKey = temp.resolve("vendor.private");
        Path publicKey = temp.resolve("vendor.public");
        Path input = temp.resolve("invalid.json");
        Path output = temp.resolve("must-not-exist.json");
        var mapper = mapper();
        var tree = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(LicenseFixtures.aInc());
        tree.put("unknownCommercialTerm", true);
        mapper.writeValue(input.toFile(), tree);
        assertThat(LicenseIssuerCli.execute("keygen", "--private-key", privateKey.toString(), "--public-key", publicKey.toString())).isZero();
        assertThat(LicenseIssuerCli.execute("issue-license", "--input", input.toString(), "--private-key", privateKey.toString(), "--public-key", publicKey.toString(), "--output", output.toString())).isNotZero();
        assertThat(output).doesNotExist();
    }
}
