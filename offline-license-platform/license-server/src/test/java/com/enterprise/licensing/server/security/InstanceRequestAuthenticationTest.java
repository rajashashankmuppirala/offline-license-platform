package com.enterprise.licensing.server.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.licensing.crypto.*;
import com.enterprise.licensing.model.*;
import com.enterprise.licensing.storage.repository.*;
import com.enterprise.licensing.storage.sqlite.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.*;
import java.util.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.*;

class InstanceRequestAuthenticationTest {
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");
    @TempDir
    Path temp;

    @Test
    void acceptsValidAttributionThenRejectsReplay() throws Exception {
        var context = context();
        var request = signed(context, "nonce", now, new byte[0]);
        var response = new MockHttpServletResponse();
        var called = new java.util.concurrent.atomic.AtomicBoolean();
        context.filter.doFilter(request, response, (a, b) -> called.set(true));
        assertThat(called).isTrue();
        var replay = signed(context, "nonce", now, new byte[0]);
        var denied = new MockHttpServletResponse();
        context.filter.doFilter(replay, denied, (a, b) -> {
        });
        assertThat(denied.getStatus()).isEqualTo(401);
        assertThat(denied.getContentAsString()).contains("INSTANCE_AUTHENTICATION_FAILED");
    }

    @Test
    void rejectsStaleTimestampAndInvalidSignature() throws Exception {
        var context = context();
        var stale = signed(context, "stale", now.minusSeconds(301), new byte[0]);
        var response = new MockHttpServletResponse();
        context.filter.doFilter(stale, response, (a, b) -> {
        });
        assertThat(response.getStatus()).isEqualTo(401);
        var invalid = signed(context, "invalid", now, new byte[0]);
        invalid.removeHeader("X-License-Signature");
        invalid.addHeader("X-License-Signature", Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[64]));
        var bad = new MockHttpServletResponse();
        context.filter.doFilter(invalid, bad, (a, b) -> {
        });
        assertThat(bad.getStatus()).isEqualTo(401);
    }

    @Test
    void rejectsWrongKeyAndBodyMutation() throws Exception {
        var context = context();
        var wrong = signed(context, "wrong", now, new byte[0], Ed25519Keys.generate());
        var wrongResponse = new MockHttpServletResponse();
        context.filter.doFilter(wrong, wrongResponse, (a, b) -> {
        });
        assertThat(wrongResponse.getStatus()).isEqualTo(401);
        var changed = signed(context, "body", now, "original".getBytes());
        changed.setContent("modified".getBytes());
        var changedResponse = new MockHttpServletResponse();
        context.filter.doFilter(changed, changedResponse, (a, b) -> {
        });
        assertThat(changedResponse.getStatus()).isEqualTo(401);
    }

    private Context context() {
        var d = new SqliteDatabase(temp.resolve(UUID.randomUUID() + ".db"));
        var t = new SqliteImmediateTransactionRunner(d);
        var key = Ed25519Keys.generate();
        var instances = new SqliteProductInstanceRepository(d, t, new ObjectMapper());
        instances.register(new InstanceRegistrationRequest("instance", new EncodedPublicKey("Ed25519", "X.509", Ed25519Keys.encodePublic(key.getPublic())), ProductId.VALIDATOR, DeploymentType.TOMCAT, EnvironmentType.PRODUCTION, "1", Map.of()), Ed25519Keys.fingerprint(key.getPublic()), 1, 1, now);
        return new Context(key, new InstanceRequestAuthenticationFilter(instances, new SqliteRequestNonceRepository(d, t), Clock.fixed(now, ZoneOffset.UTC)));
    }

    private MockHttpServletRequest signed(Context c, String nonce, Instant timestamp, byte[] body) {
        String path = "/api/v1/license/runtime/entitlements/products/VALIDATOR";
        var request = new MockHttpServletRequest("GET", path);
        request.setContent(body);
        request.addHeader("X-License-Instance-Id", "instance");
        request.addHeader("X-License-Timestamp", timestamp.toString());
        request.addHeader("X-License-Nonce", nonce);
        byte[] content = new InstanceRequestCanonicalizer().canonicalize("GET", path, timestamp.toString(), nonce, body);
        request.addHeader("X-License-Signature", Base64.getUrlEncoder().withoutPadding().encodeToString(Ed25519Keys.sign(c.key.getPrivate(), content)));
        return request;
    }

    private MockHttpServletRequest signed(Context c, String nonce, Instant timestamp, byte[] body, java.security.KeyPair signingKey) {
        String path = "/api/v1/license/runtime/entitlements/products/VALIDATOR";
        var request = new MockHttpServletRequest("GET", path);
        request.setContent(body);
        request.addHeader("X-License-Instance-Id", "instance");
        request.addHeader("X-License-Timestamp", timestamp.toString());
        request.addHeader("X-License-Nonce", nonce);
        byte[] content = new InstanceRequestCanonicalizer().canonicalize("GET", path, timestamp.toString(), nonce, body);
        request.addHeader("X-License-Signature", Base64.getUrlEncoder().withoutPadding().encodeToString(Ed25519Keys.sign(signingKey.getPrivate(), content)));
        return request;
    }

    private record Context(java.security.KeyPair key, InstanceRequestAuthenticationFilter filter) {
    }
}
