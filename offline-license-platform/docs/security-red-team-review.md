# Security Red-Team Review

**Reviewed:** 2026-09-09

**Scope:** Central offline License Server POC, including its Java client and sample integrations

**Adversary:** Customer personnel with root on product and License Server hosts, Kubernetes
cluster-admin, database and filesystem access, VM snapshot control, and internal network access

## Executive verdict

The POC is effective at three things when the released software and configured trust anchors remain
intact:

1. A customer cannot modify a vendor-signed commercial license or activation certificate and have
   the unmodified server accept it.
2. An independently initialized License Server cannot use an activation issued to another server.
3. One running SQLite authority serializes honest registrations, leases, assignments, agent
   allocations, and metering without race-based over-allocation.

It is **not** an enforcement boundary against the adversary in this review. A privileged customer
can copy exportable identities, clone the complete authority, replace the configured vendor public
key, change unsigned enforcement settings, patch product or client code, suppress metering calls,
or reuse logical identities. Those attacks can yield every target outcome: extra environments,
users, agents, AI usage, and use after expiry.

The most consequential result is that “cryptographically signed” does not mean “root-resistant.”
The vendor private key is correctly absent from customer runtime artifacts, but the trusted vendor
public key is loaded from a customer-writable file and both server and product private keys are
exportable software keys. Full clone prevention and trustworthy local enforcement require at least
a non-exportable hardware identity plus measured/verified boot or an external authority the
customer cannot clone. Human identity and actual AI-work accounting additionally require trusted
identity and workload telemetry; hardware alone is not sufficient for those two problems.

## Classification meanings

- **PREVENTED** — the unmodified implementation rejects the attack and does not grant the target
  capacity. This does not claim resistance to binary replacement by root.
- **DETECTED** — the implementation fails closed or leaves explicit evidence, but detection is the
  primary control and a fully privileged attacker may erase or forge that evidence.
- **RAISES COST ONLY** — the control stops casual abuse or creates forensic friction, but the stated
  adversary has a practical software-only bypass.
- **NOT PREVENTABLE WITHOUT HARDWARE TRUST** — an offline software process cannot distinguish the
  attack from an authorized restored or executing system under the stated host control. Some rows
  also need an external identity/telemetry authority or server-side product enforcement.

## Critical architectural findings

### CRITICAL: the vendor trust anchor is replaceable

**Classification: NOT PREVENTABLE WITHOUT HARDWARE TRUST**

`ModuleConfiguration.activationService()` and `activeLicenseVerifier()` read the trusted vendor
public key on demand from `licensing.vendor-public-key-file`. `application.yml` obtains that path
from `LICENSE_SERVER_VENDOR_PUBLIC_KEY_FILE`. Neither the key nor its expected fingerprint is
compiled into a protected component, signed by a higher trust anchor, or covered by the
authoritative-state checkpoint.

A root administrator can generate an attacker key pair, point the server at the attacker public
key, and sign a license and activation granting arbitrary limits. The normal import path then seals
that state and audits it as valid. This needs neither database editing nor a patched License Server.
The runtime artifact scan proves the vendor **private** key is absent; it does not protect the
vendor **public** trust anchor from replacement.

Responsible implementation:

- `license-server/src/main/java/com/enterprise/licensing/server/config/ModuleConfiguration.java` — `activationService()`
  and
  `activeLicenseVerifier()`
- `license-server/src/main/resources/application.yml` — `vendor-public-key-file`
- `license-crypto/src/main/java/com/enterprise/licensing/crypto/runtime/CommercialLicenseVerifier.java` and
  `license-crypto/src/main/java/com/enterprise/licensing/crypto/runtime/ActivationVerifier.java` —
  correctly verify against whichever public key configuration supplies

### CRITICAL: complete License Server cloning forks all accounting

**Classification: NOT PREVENTABLE WITHOUT HARDWARE TRUST**

`SoftwareIdentityProvider` stores the License Server ID, public key, and private key together in
`identity.json`. Copying the VM therefore copies the exact identity needed by
`ActiveLicenseVerifier`, `AuthoritativeStateIntegrityService`, cached-entitlement signing, and
trusted-time checkpoint verification. If the SQLite database and checkpoint files are cloned at
the same point, every clone starts from valid state and independently grants the same remaining
environment, user, agent, and meter capacity. No clone knows that another clone exists.

Responsible implementation:

- `license-storage/src/main/java/com/enterprise/licensing/storage/identity/SoftwareIdentityProvider.java` — exportable
  Base64 private key in
  the persisted identity record; owner-only mode protects against ordinary users, not root
- `license-engine/src/main/java/com/enterprise/licensing/engine/validation/ActiveLicenseVerifier.java` — binds
  activation to that copied ID
  and fingerprint
- `license-storage/src/main/java/com/enterprise/licensing/storage/integrity/AuthoritativeStateIntegrityService.java`
  and `license-storage/src/main/java/com/enterprise/licensing/storage/integrity/FileTrustedTimeCheckpoint.java`
  — signatures use the same copied software identity
- `docs/threat-model.md` and the feature specification — explicitly defer advanced full-VM clone
  detection

### CRITICAL: product-side checks are advisory under product-host root

**Classification: NOT PREVENTABLE WITHOUT HARDWARE TRUST**

`DefaultLicenseClient`, `UserLeaseManager`, and `AgentLeaseManager` return decisions or local
validity to product code. Nothing makes those calls an unavoidable gateway to the product's useful
work. Root can patch the client, patch call sites, replace a JAR, invoke a product API that omitted
the check, or force a positive return. The License Server cannot observe that bypass.

Responsible implementation:

- `license-client-java/src/main/java/com/enterprise/licensing/client/DefaultLicenseClient.java`
- `license-client-java/src/main/java/com/enterprise/licensing/client/lease/UserLeaseManager.java`
  and `license-client-java/src/main/java/com/enterprise/licensing/client/lease/AgentLeaseManager.java`
- `examples/sample-product/src/main/java/com/enterprise/licensing/example/product/SampleProductApplication.java`
  — demonstrates cooperative
  enforcement, not an unbypassable reference monitor

For meaningful resistance, licensed operations must execute behind a service boundary the
customer cannot alter, or the product must use measured code and non-exportable keys with remote or
local attestation. API coverage tests cannot establish this property.

### HIGH: identities describe logical names, not physical deployments or humans

**Classification: RAISES COST ONLY**

Product instance IDs, session IDs, user IDs, agent IDs, and execution IDs are supplied by the
customer-controlled product. The server proves possession of an instance key but does not prove
which cluster, host, person, process, or AI execution is using it. This is useful operational
accounting for cooperative products, not adversarial subject counting.

There is also a concrete idempotency weakness: `SqliteUserLicenseRepository.acquire()` returns an
existing active lease for `(instanceId, sessionId)` without checking that the requested `userId`
and `accountType` match the existing lease. A hostile caller can reuse one session ID for different
declared users and receive the same accepted lease. `SqliteAiRepository.acquire()` similarly
returns the existing active lease for `(agentId, executionId)`, allowing arbitrary work to fan out
behind one execution identity.

### HIGH: enforcement configuration is not licensed or integrity-protected

**Classification: RAISES COST ONLY**

Root can override Spring properties for AEU weights, lease durations, cached-entitlement duration,
and clock-rollback tolerance. `ConfiguredAEUPolicy` requires weights to be positive but does not
bind them to the signed commercial license. A customer can set every AI action to weight 1, enlarge
lease/cache durations, or make rollback tolerance ineffective without editing authoritative rows.

Responsible implementation:

- `license-server/src/main/java/com/enterprise/licensing/server/config/ModuleConfiguration.java` —
  `aeuPolicy()`, `userLicenseService()`,
  `agentLeaseService()`, `cachedEntitlementService()`, and `trustedTimeService()`
- `license-server/src/main/resources/application.yml`
- `license-engine/src/main/java/com/enterprise/licensing/engine/metering/ConfiguredAEUPolicy.java`

## Attack-by-attack assessment

### Copy or modify the master commercial license

**PREVENTED** for an activation-required license on an unmodified server with the intended vendor
public key. `CommercialLicenseVerifier` verifies the Ed25519 envelope and
`ActiveLicenseVerifier.verify()` re-verifies the persisted envelope before every protected engine
decision. Modifying capacity, validity, products, pools, or policy invalidates the signature.

Copying an unchanged master license is harmless by itself because
`ActivationService.importArtifacts()` requires a matching activation, and
`ActiveLicenseVerifier` requires matching license ID, customer ID, server ID, key fingerprint, and
activation policy. If a vendor intentionally issues a license with `ActivationPolicy.required`
false, the code explicitly permits activation-free use; copying is then allowed by policy.

**Privileged bypass:** replacing the vendor trust anchor or patching verification changes the
classification to **NOT PREVENTABLE WITHOUT HARDWARE TRUST**.

### Copy the activation certificate

**PREVENTED** when copied to an independently initialized server. `ActivationService` and
`ActiveLicenseVerifier` compare the certificate's server UUID and public-key fingerprint with
`LicenseServerIdentityService.publicIdentity()`. The activation envelope is independently verified
with the vendor key.

**Privileged bypass:** copying the matching server identity, or the entire VM, changes the result to **NOT PREVENTABLE
WITHOUT HARDWARE TRUST**. The certificate binds to a key, not to non-clonable
hardware.

### Copy the License Server VM

**NOT PREVENTABLE WITHOUT HARDWARE TRUST.** A consistent snapshot contains the exportable server
private key, database, state-integrity checkpoint, trusted-time checkpoint, admin configuration,
and vendor public key. Multiple restored clones all satisfy the same bindings and maintain
independent counters. This yields extra environments, concurrent users, named users, agents, and
AI usage.

### Run multiple License Servers

- **PREVENTED** when each server creates an independent identity and all use the intended vendor
  trust anchor: a certificate for server A is rejected by server B.
- **NOT PREVENTABLE WITHOUT HARDWARE TRUST** when servers share a copied identity and cloned state.
  Each fork is locally valid and there is no online quorum, vendor callback, monotonic hardware
  counter, or clone registry.
- **NOT PREVENTABLE WITHOUT HARDWARE TRUST** when root replaces the vendor public key and issues new
  attacker-signed activations for arbitrary server identities.

`ActivationPolicy.maxLicenseServers` is carried inside the signed artifacts, but this customer-side
offline server does not maintain a vendor-global activation ledger. Issuance discipline is
therefore external to this repository.

### Copy a PostgreSQL database

**RAISES COST ONLY — PostgreSQL is not implemented.** The repository contains SQLite JDBC,
`SqliteDatabase`, SQLite migrations, `BEGIN IMMEDIATE`, and `VACUUM INTO`; it has no PostgreSQL
adapter, migration set, locking strategy, backup integration, or PostgreSQL test. No claim of
PostgreSQL copy prevention is supported by observable code.

At the engine layer, `ActiveLicenseVerifier` would reject signed state copied onto a different
server identity. That backend-neutral check is not a substitute for a PostgreSQL implementation,
and a database copied together with the software identity has the same full-clone weakness.

### Restore an older PostgreSQL backup

**RAISES COST ONLY — PostgreSQL is not implemented.** There is no PostgreSQL restore path to
validate. The stale-restore evidence applies only to the wired SQLite implementation.

For SQLite, restoring only an old database while retaining the newer separate checkpoints is **DETECTED** by
`FileTrustedTimeCheckpoint` sequence comparison and by the full-state digest in
`AuthoritativeStateIntegrityService`; protected API calls fail closed and recovery becomes
required. `SqliteBackupService.validateForOriginalIdentity()` also rejects a backup from another
server identity.

Restoring the database **and** matching old identity/checkpoint files, then restoring or controlling
wall time, is **NOT PREVENTABLE WITHOUT HARDWARE TRUST**. All signed rollback evidence has been
rolled back together.

### Roll the License Server clock backward

- **DETECTED** for an ordinary rollback greater than five minutes. `SqliteTrustedTimeRepository`
  persists the highest accepted time; `FileTrustedTimeCheckpoint` independently signs time and
  sequence; `LicenseValidationService` fails closed with `CLOCK_ROLLBACK_DETECTED`; `RecoveryService`
  enters `RECOVERY_REQUIRED`.
- **PREVENTED** as a gradual rollback trick under fixed configuration: the highest time does not
  move backward, so repeated small changes cannot ratchet it down.
- **RAISES COST ONLY** because root can enlarge `licensing.clock-rollback-tolerance`, extract the
  server private key and create consistent older checkpoints, or patch `TimeSource`/validation.
- **NOT PREVENTABLE WITHOUT HARDWARE TRUST** for a full snapshot rollback with a controlled/frozen
  clock. An offline software clock cannot prove elapsed real time after all local state is restored.

### Continue using a cached entitlement after expiry

**PREVENTED** against file-only modification or copying to a different identity. The server caps a
certificate at the lesser of configured cache duration and commercial grace expiry.
`CachedEntitlementVerifier` verifies the server signature, server ID, product instance ID, instance
fingerprint, and absolute expiry. `FileEntitlementCache` adds AES-GCM integrity/confidentiality
derived from the product key.

**NOT PREVENTABLE WITHOUT HARDWARE TRUST** under product-host root. `DefaultLicenseClient` verifies
expiry with its supplied local `Clock`, and the lease managers also evaluate absolute expiry with a
local `Clock`. Root can freeze/roll back that clock, extract the product key, patch the verifier, or
replace the decision. AES-GCM protects against casual copying, not the root user who owns its key.

### Obtain extra environment registrations through normal requests

**PREVENTED** on one honest authority. `InstanceRegistrationService` derives limits from the signed
license. `SqliteProductInstanceRepository.register()` counts non-decommissioned instances and
performs count-and-insert inside `SqliteImmediateTransactionRunner.write()` using `BEGIN IMMEDIATE`.
The active PROD/DR handoff is also serialized in `switchActive()`.

This protection is defeated by a full authority clone, a replaced trust anchor, or shared logical
instance credentials as described separately.

### Register many Kubernetes clusters using one identity

**NOT PREVENTABLE WITHOUT HARDWARE TRUST.** The implementation intentionally treats every caller
holding the same product identity as one logical installation. Registration is idempotent by
instance ID/fingerprint/product/environment/deployment type. Signed runtime requests prove key
possession, not cluster UID, namespace, hardware, or cluster count. A customer can mount one copied
identity secret into any number of clusters; the License Server sees one instance.

Responsible implementation:

- `license-client-java/src/main/java/com/enterprise/licensing/client/identity/ProductInstanceIdentityStore.java`
- `license-storage/src/main/java/com/enterprise/licensing/storage/repository/SqliteProductInstanceRepository.java`
  — existing-instance branch
- `license-server/src/main/java/com/enterprise/licensing/server/security/InstanceRequestAuthenticationFilter.java`
-
`examples/kubernetes-example/src/main/java/com/enterprise/licensing/example/kubernetes/SharedInstallationIdentity.java`

This design correctly handles replicas within one cluster but has no cryptographic way to
distinguish those replicas from copies in unrelated clusters.

### Spoof a product instance ID

**PREVENTED** if the attacker knows only the ID. `InstanceRequestAuthenticationFilter` verifies an
Ed25519 signature over method, URI/query, timestamp, nonce, and body using the public key registered
for the instance. It applies a five-minute window and persists one-use nonces in
`SqliteRequestNonceRepository`. A header-only spoof or captured-request replay fails.

**NOT PREVENTABLE WITHOUT HARDWARE TRUST** once root reads the product identity file. The private
key is stored as Base64 PKCS#8 in `ProductInstanceIdentityStore`, protected only by owner-only file
permissions. Root can sign arbitrary requests for that ID.

### Share product instance credentials

**NOT PREVENTABLE WITHOUT HARDWARE TRUST.** Every holder of the copied private key is the same
instance to the server. Timestamp and nonce validation prevent replay of one packet but do not
prevent multiple holders from generating fresh valid packets. There is no mTLS hardware key,
attestation, location binding, or simultaneous-use detector.

### Obtain extra concurrent users through normal lease contention

**PREVENTED** on one honest authority. `UserLicenseService.acquireLease()` requires an ACTIVE
instance and human account, obtains the signed customer/pool limit, and
`SqliteUserLicenseRepository.acquire()` performs the live count and insert atomically. Absolute
server expiry prevents abandoned leases from retaining a slot forever.

### Reuse or share one concurrent-user lease

**RAISES COST ONLY.** The License Server counts declared active leases, not people or product
requests. A patched product can let many people share one licensed user identity or can accept work
without acquiring a lease. More specifically, reusing `(instanceId, sessionId)` returns an existing
active lease without verifying the new request's `userId` or `accountType` against that lease.

Responsible implementation: `SqliteUserLicenseRepository.acquire()` and the schema's
`UNIQUE(instance_id, session_id)`. There is no trusted IdP subject binding or server-side product
session enforcement. Audit records show the requested user on acquisition, but there is no
automatic mismatch detection in the idempotent branch.

### Change user-lease rows

- **DETECTED** for a direct edit, deletion, expiry extension, or release/revocation removal that
  does not also update the checkpoint. `user_lease` is in
  `AuthoritativeStateIntegrityService.PROTECTED_TABLES`; the next licensing request returns
  `STATE_INTEGRITY_FAILURE` before granting work.
- **RAISES COST ONLY** against root. The state digest is signed with the exportable server identity
  key. Root can extract that key and reproduce a consistent checkpoint, invoke patched sealing
  code, or replace the server binary. The control detects accidental/naive edits, not a key-holding
  administrator.

### Obtain extra named users through normal assignment contention

**PREVENTED** on one honest authority. `UserLicenseService.assignNamed()` derives global, category,
and product limits from the signed license. `SqliteUserLicenseRepository.assign()` applies those
limits and the 30-day cooldown inside one immediate transaction.

### Alias people or churn named seats

- **RAISES COST ONLY** for multiple people sharing one declared `userId`. The server has no trusted
  link to a corporate IdP and cannot know how many humans use the alias.
- **DETECTED** for using the administrative departure/correction override to churn seats. The API
  requires actor, justification, prior user, replacement user, and a permitted override type;
  `AdministrationController` and `DomainDecisionAudit` record these. However, the single bearer
  admin token supplies all authority, and host root can obtain it. This is forensic control, not
  separation of duties.
- Direct assignment-row edits have the same **DETECTED** then **RAISES COST ONLY** split as lease-row
  edits because `named_user_assignment` is in the protected-table digest.

### Obtain extra registered agents or concurrent executions normally

**PREVENTED** on one honest authority. `AgentRegistryService` and `AgentLeaseService` validate the
signed AI entitlement, active instance, capability, and pool. `SqliteAiRepository.register()` and
`acquire()` serialize count-and-insert operations and return
`AGENT_REGISTRATION_LIMIT_EXCEEDED` or `AGENT_CONCURRENCY_LIMIT_EXCEEDED` at the boundary.

### Alias agents or reuse an execution lease

**RAISES COST ONLY.** Agent IDs have no independent credentials; registration is an admin-token
operation naming an arbitrary string. A customer can run many actual agents behind one declared
agent. `SqliteAiRepository.acquire()` treats `(agentId, executionId)` as idempotent and returns one
existing active lease; many workers can share that logical execution. The License Server does not
observe worker count or execution fan-out.

Changing `registered_agent` or `agent_lease` rows directly is **DETECTED** by the authoritative
digest, but a root user with the exportable server key can reseal or patch around it, so the
root-resistant value **RAISES COST ONLY**.

### Obtain extra AI usage through race conditions

**PREVENTED** on one honest authority. `SqliteMeteringRepository.consume()` performs an atomic
conditional increment (`used_amount + amount <= licensed_limit`) and records the accepted or denied
consumption in the same transaction. The primary key on customer/pool/meter/period and immediate
write transaction prevent last-unit oversubscription. Request IDs are bound to the original
customer, instance, pool, meter, action, amount, period, and dimensions before an idempotent result
is replayed.

### Delete meter rows or lower AI counters

- **DETECTED** for a naive change. `meter_usage` and `usage_consumption` are both included in
  `AuthoritativeStateIntegrityService.PROTECTED_TABLES`; the next request fails closed. The audit
  table is protected too.
- **RAISES COST ONLY** for the stated adversary. The digest checkpoint and audit chain are locally
  reproducible once the exportable server key and code are controlled. The audit event hashes are
  SHA-256 chaining, not signatures from an external witness.
- **NOT PREVENTABLE WITHOUT HARDWARE TRUST** when counters are forked by cloning the complete
  License Server before consumption.

### Under-report or suppress AI work

**NOT PREVENTABLE WITHOUT HARDWARE TRUST** (and trusted workload telemetry). The meter counts API
calls, not actual AI work. A patched product can omit `consumeAction()`, reuse one request ID for
multiple real actions, label autonomous work as cheaper interactive work, or perform work after a
denial. `MeteringService` trusts caller-supplied `requestId`, `actionType`, and dimensions. It maps
the declared action through `ConfiguredAEUPolicy`; it does not bind consumption to an agent lease
or independently observed execution.

Repeated identical request IDs create audit events and may be forensically suspicious, but no code
automatically detects “one reported request represented many real executions.” Locally mutable AEU
weights further weaken this boundary.

### Continue access after commercial expiration through the License Server

**PREVENTED** on an honest, correctly configured server. `LicenseValidationService` denies after
`graceUntil`, denies new instance/named-user/agent registrations during grace, checks revocation,
and invokes active-artifact and state verification for every engine operation.
`CachedEntitlementService` never signs a cache beyond `graceUntil`.

**RAISES COST ONLY** under server root because clock tolerance and binaries are controllable and the
vendor trust anchor can be replaced with one that signs a later license.

**NOT PREVENTABLE WITHOUT HARDWARE TRUST** on the product host: cached checks and lease validity use
the product's local clock and cooperative client code, both controlled by root. Full VM snapshots
can also restore an earlier valid product state and clock.

### Patch `license-client` code

**NOT PREVENTABLE WITHOUT HARDWARE TRUST.** The server continues to protect its own allocation APIs,
but a patched client can return allowed decisions, ignore lease expiry, skip metering, or never call
the server. JAR scanning and tests verify build contents; they provide no runtime code integrity or
attestation on a customer-controlled host.

### Call product APIs while bypassing License Server checks

**NOT PREVENTABLE WITHOUT HARDWARE TRUST** or a separately controlled server-side reference
monitor. The POC supplies a client library and examples, not an API gateway that mediates every
valuable product operation. Any endpoint or code path that omits `requireProduct`, `requireFeature`,
lease validation, or metering is invisible to the License Server. Even correctly placed calls can
be patched by root.

### Replay or alter signed runtime API calls on the internal network

**PREVENTED** without the product private key. `SignedInstanceRequestFactory` signs the method,
path/query, timestamp, nonce, and body. `InstanceRequestAuthenticationFilter` verifies all fields,
rejects stale requests, and persists nonce uniqueness. Production startup requires TLS through
`ProductionTlsGuard`, and `JdkLicenseTransport` supports public-key pinning.

Internal network access becomes sufficient when combined with an extracted product key or admin
token. The global admin bearer token is used by `DefaultLicenseClient` for status and registration;
root on any configured product host can read it and invoke all administrative endpoints. Limits
still apply on an honest authority, but governance and role separation do not.

## Control summary by desired outcome

| Desired unauthorized outcome | Honest single-authority result                           | Result under stated privileged adversary                                                                                         |
|------------------------------|----------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------|
| Extra environments           | Atomic limit is **PREVENTED**                            | Clone authority or share one cluster identity: **NOT PREVENTABLE WITHOUT HARDWARE TRUST**                                        |
| Extra concurrent users       | Last-slot race is **PREVENTED**                          | Share identity/session/lease or patch product: **RAISES COST ONLY** / **NOT PREVENTABLE WITHOUT HARDWARE TRUST**                 |
| Extra named users            | Assignment limits/cooldown are **PREVENTED**             | Alias people, use privileged override, reseal state, or patch product: **RAISES COST ONLY**                                      |
| Extra agents                 | Registration/concurrency race is **PREVENTED**           | Alias agents/executions or clone authority: **RAISES COST ONLY** / **NOT PREVENTABLE WITHOUT HARDWARE TRUST**                    |
| Extra AI usage               | Atomic reported-meter quota is **PREVENTED**             | Suppress/mislabel/replay reporting, alter weights, patch product, or clone authority: **NOT PREVENTABLE WITHOUT HARDWARE TRUST** |
| Access after expiry          | Honest server and honest-clock cache deny: **PREVENTED** | Replace trust anchor, control time, restore full snapshot, or patch product: **NOT PREVENTABLE WITHOUT HARDWARE TRUST**          |

## What the implementation can safely claim

The POC can claim cryptographic artifact integrity against parties that cannot replace the vendor
trust anchor; activation binding against independently generated server identities; replay-resistant
instance API authentication while instance keys remain secret; fail-closed detection of naive
SQLite row edits and partial rollback; and atomic accounting within one unmodified SQLite License
Server.

It should not claim resistance to a malicious customer administrator with the access assumed here,
physical Kubernetes-cluster counting, physical human or agent counting, trustworthy usage telemetry,
PostgreSQL behavior, full-clone prevention, or unbypassable product enforcement.

## Hardening priorities

1. Place the License Server identity in a TPM/HSM as non-exportable material; seal it to measured
   boot and sign monotonic state with a hardware counter.
2. Pin the vendor trust anchor in a verified component or secure-boot policy, and include all
   enforcement configuration in vendor-signed policy. Fail startup if runtime values diverge.
3. Define an HA/clone model with a single shared transactional authority or quorum and a monotonic
   fencing token. Offline independent clones cannot safely allocate from one pool.
4. Do not distribute a global admin token to products. Separate bootstrap registration from
   day-to-day status, use narrowly scoped credentials, and require proof of possession at
   registration.
5. Bind idempotent user-session requests to the original user/account type, and bind agent
   executions to independently authenticated agent identities. Integrate a trusted enterprise IdP
   if “human” counts must resist aliases.
6. Bind metering to server-observed operations or attested workload events; bind action type to the
   active agent lease/capability. Client-reported usage cannot be adversary-proof.
7. Put valuable product operations behind an unavoidable enforcement boundary, or add verified
   boot/code measurement and attestation for local product enforcement.
8. If PostgreSQL is required, implement and test PostgreSQL-native serial allocation, integrity,
   backup/restore, fencing, and rollback behavior before making any PostgreSQL security claim.
