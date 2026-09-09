package com.enterprise.licensing.client.transport;

import java.net.URI;

public final class EndpointSecurity {
    private EndpointSecurity() {
    }

    public static void requireAllowed(URI uri, boolean allowLoopbackHttp) {
        if (uri == null || !uri.isAbsolute() || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("License Server endpoint must be an absolute server URI");
        if ("https".equalsIgnoreCase(uri.getScheme())) return;
        if (allowLoopbackHttp && "http".equalsIgnoreCase(uri.getScheme()) && isLoopbackName(uri.getHost())) return;
        throw new IllegalArgumentException("License Server endpoint must use HTTPS");
    }

    private static boolean isLoopbackName(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "[::1]".equals(host) || "::1".equals(host);
    }
}
