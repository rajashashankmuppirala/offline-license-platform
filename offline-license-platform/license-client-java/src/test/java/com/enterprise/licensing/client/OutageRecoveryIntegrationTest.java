package com.enterprise.licensing.client;

import static org.assertj.core.api.Assertions.*;

import com.enterprise.licensing.client.cache.FileEntitlementCache;
import com.enterprise.licensing.client.identity.ProductInstanceIdentityStore;
import com.enterprise.licensing.client.transport.*;
import com.enterprise.licensing.crypto.*;
import com.enterprise.licensing.crypto.runtime.LocalIdentityEnvelopeSigner;
import com.enterprise.licensing.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.*;
import java.util.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OutageRecoveryIntegrationTest {
    @TempDir
    Path temp;

    @Test
    void signedCacheAllowsOnlyProductAndFeatureChecksUntilExpiry() {
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var now = Instant.parse("2026-01-01T00:00:00Z");
        var instanceKey = Ed25519Keys.generate();
        var serverKey = Ed25519Keys.generate();
        var instance = new ProductInstanceIdentity("instance", new EncodedPublicKey("Ed25519", "X.509", Ed25519Keys.encodePublic(instanceKey.getPublic())), Ed25519Keys.fingerprint(instanceKey.getPublic()), now);
        var material = new ProductInstanceIdentityStore.Material(instance, instanceKey.getPublic(), instanceKey.getPrivate());
        var certificate = new CachedEntitlementCertificate("1.0", "cache", "license", "server", "instance", instance.publicKeyFingerprint(), now, now.plus(Duration.ofHours(24)), List.of(new ProductEntitlement(ProductId.VALIDATOR, true, 5, Set.of(new FeatureId("TABLEAU")))), Set.of(LicenseOperation.PRODUCT_CHECK, LicenseOperation.FEATURE_CHECK));
        var canonical = new CanonicalJson(mapper);
        var envelope = new LocalIdentityEnvelopeSigner(canonical).signServerArtifact(certificate, "1.0", ArtifactType.CACHED_ENTITLEMENT, serverKey.getPrivate(), serverKey.getPublic());
        var cacheFile = temp.resolve("cache/entitlement.json");
        new FileEntitlementCache(cacheFile, new EnvelopeJsonCodec(mapper), material.privateKey().getEncoded()).save(envelope);
        var offline = new LicenseTransport() {
            public Response exchange(String m, URI u, Map<String, String> h, byte[] b, Duration t) throws IOException {
                throw new IOException("down");
            }
        };
        var client = new DefaultLicenseClient(URI.create("https://license.internal"), material, "server", serverKey.getPublic(), "admin", mapper, offline, cacheFile, Clock.fixed(now.plusSeconds(1), ZoneOffset.UTC), Duration.ofSeconds(1));
        assertThat(client.product(ProductId.VALIDATOR).allowed()).isTrue();
        assertThat(client.product(new ProductId("DATA_MANAGER")).reasonCode()).isEqualTo(LicenseReasonCode.PRODUCT_NOT_ENTITLED);
        assertThat(client.feature(ProductId.VALIDATOR, new FeatureId("TABLEAU")).allowed()).isTrue();
        assertThat(client.feature(ProductId.VALIDATOR, new FeatureId("UNLICENSED")).reasonCode()).isEqualTo(LicenseReasonCode.FEATURE_NOT_ENTITLED);
        assertThat(client.productUserCapacity(ProductId.VALIDATOR, "u").reasonCode()).isEqualTo(LicenseReasonCode.LICENSE_SERVER_UNAVAILABLE);
        assertThatThrownBy(() -> client.acquireUser("u", AccountType.HUMAN, "s")).isInstanceOfSatisfying(LicenseClientDeniedException.class, e -> assertThat(e.reasonCode()).isEqualTo(LicenseReasonCode.LICENSE_SERVER_UNAVAILABLE));
        var expired = new DefaultLicenseClient(URI.create("https://license.internal"), material, "server", serverKey.getPublic(), "admin", mapper, offline, cacheFile, Clock.fixed(now.plus(Duration.ofHours(24)).plusSeconds(1), ZoneOffset.UTC), Duration.ofSeconds(1));
        assertThat(expired.product(ProductId.VALIDATOR).reasonCode()).isEqualTo(LicenseReasonCode.CACHED_ENTITLEMENT_EXPIRED);
    }
}
