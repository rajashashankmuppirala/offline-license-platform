package com.enterprise.licensing.server.security;

import com.enterprise.licensing.crypto.*;
import com.enterprise.licensing.engine.instance.ProductInstanceRepository;
import com.enterprise.licensing.model.InstanceState;
import com.enterprise.licensing.storage.repository.SqliteRequestNonceRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.Base64;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(20)
public final class InstanceRequestAuthenticationFilter extends OncePerRequestFilter {
    private final ProductInstanceRepository instances;
    private final SqliteRequestNonceRepository nonces;
    private final Clock clock;
    private final Duration window = Duration.ofMinutes(5);
    private final InstanceRequestCanonicalizer canonicalizer = new InstanceRequestCanonicalizer();

    @org.springframework.beans.factory.annotation.Autowired
    public InstanceRequestAuthenticationFilter(ProductInstanceRepository i, SqliteRequestNonceRepository n) {
        this(i, n, Clock.systemUTC());
    }

    InstanceRequestAuthenticationFilter(ProductInstanceRepository i, SqliteRequestNonceRepository n, Clock c) {
        instances = i;
        nonces = n;
        clock = c;
    }

    private static String required(HttpServletRequest r, String name) {
        String v = r.getHeader(name);
        if (v == null || v.isBlank()) throw new IllegalArgumentException();
        return v;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest r) {
        return !r.getRequestURI().startsWith("/api/v1/license/runtime");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        byte[] body = request.getInputStream().readAllBytes();
        var wrapped = new BodyRequest(request, body);
        try {
            String instanceId = required(request, "X-License-Instance-Id"), timestampText = required(request, "X-License-Timestamp"), nonce = required(request, "X-License-Nonce"), signatureText = required(request, "X-License-Signature");
            var instance = instances.find(instanceId).orElseThrow();
            if (instance.state() == InstanceState.DECOMMISSIONED) throw new IllegalArgumentException();
            Instant timestamp = Instant.parse(timestampText), now = clock.instant();
            if (Duration.between(timestamp, now).abs().compareTo(window) > 0) throw new IllegalArgumentException();
            var key = Ed25519Keys.decodePublic(instance.installationPublicKey().encoded());
            if (!Ed25519Keys.fingerprint(key).equals(instance.publicKeyFingerprint()))
                throw new IllegalArgumentException();
            String path = request.getRequestURI() + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
            if (!Ed25519Keys.verify(key, canonicalizer.canonicalize(request.getMethod(), path, timestampText, nonce, body), Base64.getUrlDecoder().decode(signatureText)))
                throw new IllegalArgumentException();
            if (!nonces.accept(instanceId, nonce, timestamp, now.plus(window))) throw new IllegalArgumentException();
            chain.doFilter(wrapped, response);
        } catch (RuntimeException e) {
            response.setStatus(401);
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"reasonCode\":\"INSTANCE_AUTHENTICATION_FAILED\"}");
        }
    }

    private static final class BodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        BodyRequest(HttpServletRequest r, byte[] b) {
            super(r);
            body = b;
        }

        @Override
        public ServletInputStream getInputStream() {
            var input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                public boolean isFinished() {
                    return input.available() == 0;
                }

                public boolean isReady() {
                    return true;
                }

                public void setReadListener(ReadListener listener) {
                }

                public int read() {
                    return input.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
