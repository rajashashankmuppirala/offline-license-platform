package com.enterprise.licensing.server.activation;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.licensing.crypto.CanonicalJson;
import com.enterprise.licensing.crypto.Ed25519Keys;
import com.enterprise.licensing.crypto.vendor.CommercialLicenseSigner;
import com.enterprise.licensing.crypto.vendor.VendorEnvelopeSigner;
import com.enterprise.licensing.client.DefaultLicenseClient;
import com.enterprise.licensing.client.TransportSecurityMode;
import com.enterprise.licensing.client.identity.ProductInstanceIdentityStore;
import com.enterprise.licensing.client.transport.JdkLicenseTransport;
import com.enterprise.licensing.model.*;
import com.enterprise.licensing.testkit.LicenseFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LicenseOnlyModeApiTest {
    private static final Path ROOT = Path.of(System.getProperty("java.io.tmpdir"),
            "license-only-http-" + UUID.randomUUID());
    private static final java.security.KeyPair VENDOR = Ed25519Keys.generate();
    private static final String ADMIN_TOKEN = "license-only-admin-token";
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @LocalServerPort
    int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        Path vendorPublic = ROOT.resolve("vendor-public.key");
        try {
            Files.createDirectories(ROOT);
            Files.writeString(vendorPublic, Ed25519Keys.encodePublic(VENDOR.getPublic()));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        registry.add("server.address", () -> "127.0.0.1");
        registry.add("licensing.activation-required", () -> false);
        registry.add("licensing.vendor-public-key-file", vendorPublic::toString);
        registry.add("licensing.data-file", () -> ROOT.resolve("license.db").toString());
        registry.add("licensing.identity-directory", () -> ROOT.resolve("identity").toString());
        registry.add("licensing.checkpoint-file", () -> ROOT.resolve("identity/time.checkpoint").toString());
        registry.add("licensing.admin-token", () -> ADMIN_TOKEN);
    }

    @Test
    void signedCommercialLicenseAloneActivatesRuntimeState() throws Exception {
        Instant now = Instant.now();
        var base = LicenseFixtures.aInc();
        var subscription = new SubscriptionEntitlement(base.subscription().type(), base.subscription().tier(),
                now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(30)), 7);
        var license = new CommercialLicense(base.schemaVersion(), "LIC-LICENSE-ONLY-001", base.customer(),
                subscription, base.activation(), base.environments(), base.users(), base.products(), base.ai(),
                now, base.licenseSequence(), base.activeProductionClassLimit(), base.revocationIds());
        var envelope = new CommercialLicenseSigner(new VendorEnvelopeSigner(new CanonicalJson(mapper)))
                .sign(license, VENDOR);

        var imported = post("/api/v1/license/admin/licenses/import", mapper.writeValueAsString(envelope));
        assertThat(imported.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(imported.body()).path("success").asBoolean()).isTrue();

        var status = mapper.readTree(get("/api/v1/license/admin/status").body()).path("data");
        assertThat(status.path("activationState").asText()).isEqualTo("ACTIVE");
        assertThat(status.path("recoveryState").asText()).isEqualTo("ACTIVE");
        assertThat(status.path("activationRequired").asBoolean()).isFalse();
        assertThat(status.path("activationCertificatePresent").asBoolean()).isFalse();
        assertThat(status.path("licenseId").asText()).isEqualTo(license.licenseId());

        var bootstrap = mapper.treeToValue(mapper.readTree(post("/api/v1/license/admin/activation/request",
                mapper.writeValueAsString(Map.of("licenseId", license.licenseId(), "customerId", license.customer().customerId(),
                        "environmentEvidence", Map.of("demo", "sample-regression")))).body()).path("data"), ActivationRequest.class);
        var identity = new ProductInstanceIdentityStore(ROOT.resolve("product/identity.json"), mapper).loadOrCreate();
        var client = new DefaultLicenseClient(URI.create("http://127.0.0.1:" + port), identity,
                bootstrap.licenseServerId().toString(), Ed25519Keys.decodePublic(bootstrap.installationPublicKey().encoded()),
                ADMIN_TOKEN, mapper, JdkLicenseTransport.localLoopbackTest(), ROOT.resolve("product/cache.json"),
                Clock.systemUTC(), Duration.ofSeconds(5), TransportSecurityMode.LOCAL_LOOPBACK_TEST);
        var instance = client.register(DeploymentType.OTHER, EnvironmentType.PRODUCTION, ProductId.VALIDATOR, "1.0", Map.of());
        assertThat(instance.state()).isEqualTo(InstanceState.REGISTERED);
        assertThat(client.activateInstance().state()).isEqualTo(InstanceState.ACTIVE);
        assertThat(client.registerAgent("sample-agent", "Sample agent").status()).isEqualTo(AgentStatus.ACTIVE);

        // Replay the same metering request with fresh sessions/executions, as a presenter rerunning the sample does.
        for (int run = 0; run < 2; run++) {
            assertThat(client.register(DeploymentType.OTHER, EnvironmentType.PRODUCTION, ProductId.VALIDATOR, "1.0", Map.of()).state())
                    .isEqualTo(InstanceState.ACTIVE);
            assertThat(client.registerAgent("sample-agent", "Sample agent").status()).isEqualTo(AgentStatus.ACTIVE);
            assertThat(client.product(ProductId.VALIDATOR).allowed()).isTrue();
            assertThat(client.feature(ProductId.VALIDATOR, new FeatureId("TABLEAU")).allowed()).isTrue();
            assertThat(client.productUserCapacity(ProductId.VALIDATOR, "sample-user").allowed()).isTrue();
            var userLease = client.acquireUser("sample-user", AccountType.HUMAN, "sample-session-" + run);
            try {
                var agentLease = client.acquireAgentExecution("sample-agent", "sample-execution-" + run, AiCapability.AUTONOMOUS_AGENT);
                try {
                    assertThat(client.consumeAction("sample-request", "AI_EXECUTION_UNIT", "INTERACTIVE_AI_REQUEST",
                            Map.of("source", "sample-product")).usedAmount()).isEqualTo(1);
                } finally {
                    client.releaseAgentExecution(agentLease.leaseId());
                }
            } finally {
                client.releaseUser(userLease.leaseId());
            }
        }
        assertThat(client.consumeAction("sample-new-request", "AI_EXECUTION_UNIT", "INTERACTIVE_AI_REQUEST",
                Map.of("source", "sample-product")).usedAmount()).isEqualTo(2);
    }

    private HttpResponse<String> get(String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Authorization", "Bearer " + ADMIN_TOKEN).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Authorization", "Bearer " + ADMIN_TOKEN)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
