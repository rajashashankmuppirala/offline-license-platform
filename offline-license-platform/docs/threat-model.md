# Threat Model

Protected assets are vendor signing material, server and instance private identities, signed entitlements, central
allocation state, usage counters, trusted time, and audit history. Controls include Ed25519 signatures, RFC 8785
canonical payloads, activation binding, signed instance requests with timestamp and one-use nonce, SQLite transactions,
absolute lease expiry, persisted trusted time, a separate signed time checkpoint, and chained audit hashes.

Malformed or modified artifacts, wrong keys, replays, stale requests, wrong-server activation, counter races, rollback
beyond tolerance, stale database restore, and corrupt audit state fail closed with stable reason codes. TLS protects
confidentiality and transport integrity; signed requests provide instance attribution.

Residual risks are explicit. A customer administrator controlling root, hypervisor, binaries, filesystem, database, and
VM snapshots can duplicate or alter the complete software system. The POC does not claim perfect VM-clone prevention.
TPM 2.0, HSM-backed non-exportable identity, measured boot, and coordinated HA remain future hardening seams.
