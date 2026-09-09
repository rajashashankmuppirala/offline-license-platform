# Phase 0 Research: Central Offline License Server

## Embedded Authoritative Storage

**Decision**: Use a dedicated SQLite 3.51.3-or-newer file owned exclusively by the single License
Server process. Enable WAL, foreign keys, `synchronous=FULL`, a bounded busy timeout, and explicit
checkpoint monitoring. Keep the database on local or block-backed storage.

**Rationale**: SQLite removes the external PostgreSQL service and keeps licensing state independent
from product databases. WAL permits readers alongside a writer, while SQLite's single-writer rule
provides a clear serialization boundary for short licensing mutations. Version 3.51.3 or a fixed
backport is required because SQLite documents a WAL-reset corruption fix at that level.

**Alternatives considered**: PostgreSQL was rejected for the POC's lightweight deployment goal;
H2 was rejected because SQLite has a more explicit file/transaction/backup operational model;
networked MariaDB remains too operationally heavy. SQLite is rejected for future multi-node HA,
where a separate server-database adapter is required.

**Sources**: [SQLite WAL](https://www.sqlite.org/wal.html),
[SQLite transactions](https://www.sqlite.org/lang_transaction.html),
[SQLite online backup](https://www.sqlite.org/backup.html).

## SQLite Transaction Pattern

**Decision**: Use Spring JDBC with explicit SQL. Every capacity-changing operation executes a short
`BEGIN IMMEDIATE` transaction, reads current valid state, performs a conditional insert/update,
writes the audit event, and commits. Retry `SQLITE_BUSY` with bounded jitter; return a stable
temporary-unavailable decision after the retry budget. Cleanup jobs never participate in
correctness.

**Rationale**: SQLite has one writer and does not provide PostgreSQL-style `SELECT FOR UPDATE`.
Acquiring the write transaction before checking capacity prevents the unsafe read/check/write
split. Partial unique indexes enforce identity/idempotency constraints; transactional checks enforce
licensed counts greater than one.

**Alternatives considered**: An in-process mutex was rejected as the security boundary because it
does not live in persistent storage and obstructs a later adapter. Optimistic read-then-update was
rejected because final-slot races can oversubscribe.

## Schema Migration

**Decision**: Use Flyway versioned SQL migrations with SQLite support and the Xerial JDBC driver.
Run migration once before accepting traffic; never allow concurrent server processes to migrate the
same file. Disable destructive clean outside tests.

**Rationale**: Flyway officially documents SQLite support and Xerial coordinates, but also states
that concurrent migration is unsupported. Single-process startup matches that limitation.

**Alternatives considered**: Application-created schema and ORM auto-DDL were rejected because they
weaken reviewability, repeatability, and corruption diagnosis.

**Source**: [Flyway SQLite database support](https://documentation.red-gate.com/flyway/reference/database-driver-reference/sqlite).

## Java and Spring Baseline

**Decision**: Compile and run on Java 21 with Spring Boot 4.1.x and Maven 3.6.3 or newer. Pin all
resolved versions in dependency management and stage the complete Maven repository before entering
an air-gapped environment. Run the POC directly as local Java processes.

**Rationale**: Spring Boot 4.1 supports Java 21, and its dependency management reduces incompatible
transitive combinations. Java 21 directly supports Ed25519 key generation and signatures.

**Alternatives considered**: An older Spring Boot line was rejected because the requested baseline
is 4.x. Docker, Testcontainers, Helm, and native-image packaging are deferred because the requested
POC execution mode is local and they add packaging dependencies without licensing value.

**Source**: [Spring Boot system requirements](https://docs.spring.io/spring-boot/system-requirements.html).

## Signed Artifact Format

**Decision**: Use a versioned signed envelope containing `envelopeVersion`, `artifactType`,
`payloadSchemaVersion`, `algorithm`, `keyId`, Base64URL-encoded canonical payload bytes, and a
Base64URL Ed25519 signature. Issuers produce payload bytes using RFC 8785 JSON Canonicalization
Scheme through `io.github.erdtman:java-json-canonicalization`; verifiers validate the signature over
the exact stored bytes before parsing the payload. Pin the reviewed library version and run the RFC
test vectors in `license-crypto`.

**Rationale**: Signing exact bytes avoids dependence on arbitrary Java/Jackson property ordering.
JCS provides a documented deterministic producer format, while retaining bytes makes verification
stable even if object serialization later changes.

**Alternatives considered**: Signing ordinary object serialization was rejected as unstable.
Custom binary canonicalization was rejected as unnecessary protocol complexity.

**Sources**: [RFC 8785](https://www.rfc-editor.org/rfc/rfc8785.html),
[Java JCS implementation](https://github.com/erdtman/java-json-canonicalization).

## Cryptography and Key Encoding

**Decision**: Use Java `KeyPairGenerator` and `Signature` with `Ed25519`; encode public keys as X.509
SubjectPublicKeyInfo and private keys as PKCS#8. Compute key IDs/fingerprints as lowercase Base64URL
SHA-256 of the DER public-key encoding. Use constant-time library verification results and reject
unknown algorithms or key IDs before parsing entitlements.

**Rationale**: Supported Java APIs avoid hand-written primitives and interoperable standard key
containers simplify offline inspection. Vendor private keys are explicit CLI file inputs and are
never defaulted or packaged.

**Alternatives considered**: RSA was rejected absent an interoperability constraint. Encryption was
rejected as the integrity mechanism; optional confidentiality can be added independently.

## License Server Identity and Backup

**Decision**: Implement an `IdentityProvider` port with `SoftwareIdentityProvider`. Persist server
UUID, public key, and permission-restricted PKCS#8 private key outside the SQLite database. Routine
database backup excludes the key. Restore on the original installation requires the same key and
matching fingerprint; new hardware or lost identity generates a new identity and requires offline
reactivation.

**Rationale**: Separate identity storage implements the clarified recovery policy and prevents a
database-only copy from becoming an activated server. The port permits later TPM/HSM providers.

**Alternatives considered**: Storing the key in SQLite was rejected because routine database backup
would export it. Automated VM-clone detection is explicitly deferred as unreliable in software.

## Product Instance Bootstrap and Authentication

**Decision**: Product setup automatically generates an Ed25519 instance key pair and UUID. Initial
registration requires an administrator bootstrap credential. Subsequent runtime requests carry
instance ID, timestamp, nonce, body digest, and Ed25519 signature; the server enforces timestamp
window and nonce uniqueness. Internal HTTPS is mandatory; mTLS remains a compatible later layer.

**Rationale**: This adds little customer interaction, proves key possession, and avoids trusting an
arbitrary header. The same identity works for Tomcat and shared Kubernetes replicas.

**Alternatives considered**: Host/pod-derived identity and unsigned instance headers were rejected.
POC mTLS was deferred because customer PKI automation is out of scope.

## Kubernetes Identity Persistence

**Decision**: The example chart runs a narrowly scoped bootstrap Job that creates a product-instance
Secret containing the UUID and key pair only when absent. All three sample replicas mount it
read-only. Customers must include that Secret in protected namespace backup; restoration preserves
identity, while absence creates a new identity that cannot claim the old registration.

**Rationale**: A Secret is simple for the POC, separates identity from pod lifecycle, and avoids an
RWX filesystem requirement. It is product-instance material, not vendor signing material.

**Alternatives considered**: Per-pod keys violate logical-installation semantics. A shared PVC adds
storage-class assumptions. A full certificate-management bootstrap is deferred with mTLS.

## Lease and Outage Defaults

**Decision**: Follow the clarified spec: five-minute user and agent leases, one-minute heartbeats,
absolute persisted expiry across restart, cached certificates valid for at most 24 hours and never
past license/grace expiry, no new offline allocations, and zero offline AEU consumption.

**Rationale**: These values are acceptance policy. The earlier planning input suggesting 120-second
leases and 30-second heartbeats is superseded by the accepted clarification and is not implemented.

**Alternatives considered**: Shorter 120/30 values were rejected to avoid contradicting the active
spec. Permissive offline allocation was rejected because global capacity cannot be guaranteed.

## Trusted Time and Restore Detection

**Decision**: Store the highest accepted timestamp and state sequence in SQLite and mirror a signed
checkpoint in a separate local file after meaningful commits. More than five minutes backward is a
clock rollback. A database sequence behind the checkpoint enters RECOVERY_REQUIRED; a complete
state deletion starts UNACTIVATED.

**Rationale**: This catches ordinary stale database restoration while honestly not defeating an
administrator who rolls back the entire VM and all files.

**Alternatives considered**: Network time is unavailable. Treating wall clock as untrusted without
state would make offline subscription enforcement ineffective.

## Future High Availability

**Decision**: Define persistence as repository and transactional-work ports owned by the engine.
Keep SQL dialect code in `license-storage`. SQLite supports only one running License Server process.
A later HA feature supplies packaging, a coordinated server-database adapter, and leader or quorum
design without changing domain or REST contracts.

**Rationale**: This keeps the POC light without falsely treating a shared SQLite file as HA.

**Alternatives considered**: SQLite on NFS and multiple License Server replicas are explicitly
rejected because WAL requires same-host shared memory and only one writer proceeds at a time.
