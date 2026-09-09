# Failure Semantics

| Operation                          | Server unavailable | Valid cache              | Default           |
|------------------------------------|--------------------|--------------------------|-------------------|
| Product or feature check           | Use cache          | Allowed entitlement only | Bounded fail-open |
| Existing leased operation          | No local extension | Until lease expiry       | Bounded fail-open |
| New user login or named assignment | Deny               | Ignored                  | Fail-closed       |
| Environment or agent registration  | Deny               | Ignored                  | Fail-closed       |
| New agent execution                | Deny               | Ignored                  | Fail-closed       |
| Usage consumption                  | Deny               | Ignored                  | Fail-closed       |

Connectivity failure maps to LICENSE_SERVER_UNAVAILABLE, expired cache to CACHED_ENTITLEMENT_EXPIRED, disallowed cache
operation to CACHED_OPERATION_NOT_ALLOWED, rollback to CLOCK_ROLLBACK_DETECTED, and corrupt state to
STATE_INTEGRITY_FAILURE. Server restart does not invalidate persisted leases, but expired leases cease counting without
cleanup.
