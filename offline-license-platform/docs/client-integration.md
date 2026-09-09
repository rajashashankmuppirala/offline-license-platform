# Java Client Integration

Each installation configures the internal HTTPS base URI, an administrator bootstrap token for initial registration only, a durable instance-identity path, a trusted server public key/server ID, a local cached-certificate path, and timeouts. Protected runtime requests sign method, path, timestamp, nonce, and body SHA-256 with the instance key. The server rejects stale time, replay, body change, or wrong keys.

Use customer PKI certificates in production and construct the JDK transport with `JdkLicenseTransport.pinned("sha256/<base64-spki-digest>")`. Normal PKIX/hostname validation and the configured SHA-256 SubjectPublicKeyInfo pin must both pass. The strict client constructor rejects non-HTTPS base URIs before sending a request.

Plain HTTP is available only by selecting both `JdkLicenseTransport.localLoopbackTest()` and `TransportSecurityMode.LOCAL_LOOPBACK_TEST`; that mode accepts only literal `localhost`, `127.0.0.1`, or `::1` endpoints. It is for isolated tests, never deployed configuration. The POC authentication contract remains mTLS-ready: a future certificate can bind the same logical instance public identity without changing entitlement APIs.
