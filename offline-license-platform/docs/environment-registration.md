# Environment Registration

One logical product installation owns one stable instance ID and key pair, product ID, deployment type, environment type, and application version. Hostname, address, pod, container, and process IDs are not identities. Reconnect with the same ID and key is idempotent; a different key is rejected.

PRODUCTION, DISASTER_RECOVERY, UAT, QA, DEVELOPMENT, and OTHER capacities are allocated transactionally. Decommission is explicit, authorized, terminal, and audited. Normal decommission refuses live leases; force mode revokes leases and cached certificates. The slot is then reusable by a new identity, while the old tombstone cannot be registered again.

For Kubernetes, every replica mounts one protected durable identity. Scaling, rescheduling, and rollout remain one installation. Namespace recreation retains entitlement only if that identity is restored. Losing it requires decommissioning the old registration and registering a new identity.
