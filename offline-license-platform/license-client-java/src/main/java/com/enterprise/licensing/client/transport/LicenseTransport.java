package com.enterprise.licensing.client.transport;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;

public interface LicenseTransport {
    Response exchange(String method, URI uri, Map<String, String> headers, byte[] body, Duration timeout) throws IOException, InterruptedException;

    record Response(int statusCode, byte[] body) {
        public Response {
            body = body == null ? new byte[0] : body.clone();
        }
    }
}
