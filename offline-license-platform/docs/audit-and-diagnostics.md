# Audit and Diagnostics

Meaningful decisions record timestamp, customer/license/instance when known, operation, entitlement, decision, reason, and relevant utilization. Events form an append-only SHA-256 chain and paging preserves stable order. Routine successful heartbeats are suppressed unless configured. Keys, seed material, credentials, bearer tokens, and secrets are redacted.

Liveness remains UP for diagnostics while UNACTIVATED or expired. Readiness requires identity, migrated SQLite storage, and integrity checks; missing/corrupt identity, migration failure, state corruption, or RECOVERY_REQUIRED is DOWN. Status reports activation, validity/grace, capacities, users, agents, meter usage, and clock warnings without secret material.
