package com.enterprise.licensing.client.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.licensing.client.identity.ProductInstanceIdentityStore;
import com.enterprise.licensing.client.security.SignedInstanceRequestFactory;
import com.enterprise.licensing.crypto.Ed25519Keys;
import com.enterprise.licensing.crypto.InstanceRequestCanonicalizer;
import com.enterprise.licensing.model.EncodedPublicKey;
import com.enterprise.licensing.model.ProductInstanceIdentity;

import java.math.BigInteger;
import java.net.URI;
import java.security.Principal;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import javax.net.ssl.X509TrustManager;

import org.junit.jupiter.api.Test;

class JdkLicenseTransportSecurityTest {
    @Test
    void strictModeRequiresHttpsAndTestModeIsLimitedToNamedLoopbackHosts() {
        assertThatCode(() -> EndpointSecurity.requireAllowed(URI.create("https://license.internal"), false)).doesNotThrowAnyException();
        assertThatThrownBy(() -> EndpointSecurity.requireAllowed(URI.create("http://license.internal"), false)).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> EndpointSecurity.requireAllowed(URI.create("http://127.0.0.1:8443"), true)).doesNotThrowAnyException();
        assertThatCode(() -> EndpointSecurity.requireAllowed(URI.create("http://localhost:8443"), true)).doesNotThrowAnyException();
        assertThatThrownBy(() -> EndpointSecurity.requireAllowed(URI.create("http://license.internal"), true)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void platformTrustAndPinnedServerKeyMustBothPassBeforeSignedRequestsAreUsed() throws Exception {
        var tlsKey = Ed25519Keys.generate();
        var accepting = new AcceptingTrustManager();
        var pinned = new JdkLicenseTransport.PinnedPublicKeyTrustManager(accepting, JdkLicenseTransport.publicKeyPin(tlsKey.getPublic()));
        assertThatCode(() -> pinned.checkServerTrusted(new X509Certificate[]{new PublicKeyCertificate(tlsKey.getPublic())}, "Ed25519")).doesNotThrowAnyException();
        assertThat(accepting.serverChecks).isEqualTo(1);
        assertThatThrownBy(() -> pinned.checkServerTrusted(new X509Certificate[]{new PublicKeyCertificate(Ed25519Keys.generate().getPublic())}, "Ed25519"))
                .isInstanceOf(CertificateException.class).hasMessageContaining("pin mismatch");

        var instanceKey = Ed25519Keys.generate();
        var now = Instant.parse("2026-01-01T00:00:00Z");
        var identity = new ProductInstanceIdentity("instance", new EncodedPublicKey("Ed25519", "X.509", Ed25519Keys.encodePublic(instanceKey.getPublic())), Ed25519Keys.fingerprint(instanceKey.getPublic()), now);
        var request = new SignedInstanceRequestFactory(new ProductInstanceIdentityStore.Material(identity, instanceKey.getPublic(), instanceKey.getPrivate()), Clock.fixed(now, ZoneOffset.UTC))
                .sign("POST", "/api/v1/license/runtime/usage", "{}".getBytes());
        byte[] canonical = new InstanceRequestCanonicalizer().canonicalize("POST", "/api/v1/license/runtime/usage", request.headers().get("X-License-Timestamp"), request.headers().get("X-License-Nonce"), request.body());
        assertThat(Ed25519Keys.verify(instanceKey.getPublic(), canonical, Base64.getUrlDecoder().decode(request.headers().get("X-License-Signature")))).isTrue();
    }

    @Test
    void malformedPinsAreRejectedBeforeAnyConnection() {
        assertThatThrownBy(() -> JdkLicenseTransport.pinned("not-a-pin")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JdkLicenseTransport.pinned("sha256/AAAA")).isInstanceOf(IllegalArgumentException.class);
    }

    private static final class AcceptingTrustManager implements X509TrustManager {
        int serverChecks;

        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            serverChecks++;
        }

        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

    @SuppressWarnings("deprecation")
    private static final class PublicKeyCertificate extends X509Certificate {
        private final PublicKey key;

        PublicKeyCertificate(PublicKey key) {
            this.key = key;
        }

        public PublicKey getPublicKey() {
            return key;
        }

        public void checkValidity() {
        }

        public void checkValidity(Date date) {
        }

        public int getVersion() {
            return 3;
        }

        public BigInteger getSerialNumber() {
            return BigInteger.ONE;
        }

        public Principal getIssuerDN() {
            return () -> "CN=test";
        }

        public Principal getSubjectDN() {
            return () -> "CN=test";
        }

        public Date getNotBefore() {
            return new Date(0);
        }

        public Date getNotAfter() {
            return new Date(Long.MAX_VALUE);
        }

        public byte[] getTBSCertificate() {
            return new byte[0];
        }

        public byte[] getSignature() {
            return new byte[0];
        }

        public String getSigAlgName() {
            return "Ed25519";
        }

        public String getSigAlgOID() {
            return "1.3.101.112";
        }

        public byte[] getSigAlgParams() {
            return null;
        }

        public boolean[] getIssuerUniqueID() {
            return null;
        }

        public boolean[] getSubjectUniqueID() {
            return null;
        }

        public boolean[] getKeyUsage() {
            return null;
        }

        public int getBasicConstraints() {
            return -1;
        }

        public byte[] getEncoded() {
            return key.getEncoded();
        }

        public void verify(PublicKey key) {
        }

        public void verify(PublicKey key, String provider) {
        }

        public String toString() {
            return "PublicKeyCertificate";
        }

        public boolean hasUnsupportedCriticalExtension() {
            return false;
        }

        public Set<String> getCriticalExtensionOIDs() {
            return Set.of();
        }

        public Set<String> getNonCriticalExtensionOIDs() {
            return Set.of();
        }

        public byte[] getExtensionValue(String oid) {
            return null;
        }
    }
}
