# Implementation Plan: Central Offline License Server

**Branch**: `001-central-offline-license-server` | **Date**: 2026-09-07 | **Spec**: `spec.md`

**Input**: Feature specification from `specs/001-central-offline-license-server/spec.md`

## Summary

Build a Java 21 and Spring Boot 4.1 multi-module platform whose single customer-hosted License
Server is authoritative for signed-license activation, logical environments, human and AI leases,
product entitlements, AEU metering, cached outage capabilities, trusted time, and audit. The POC
uses a dedicated embedded SQLite database owned only by the License Server, eliminating a customer
database service and any coupling to product databases. A storage port isolates SQLite-specific
transactions so a later HA edition can add a server-database adapter.

Commercial licenses and activation certificates use Ed25519 signed envelopes. Vendor signing code
exists only in the issuer CLI; runtime modules receive verification APIs and public keys. Product
instances generate one persistent key pair and authenticate requests with signatures over internal
HTTPS. The server uses five-minute leases, one-minute heartbeats, 24-hour maximum cached
entitlements, and fail-closed capacity-increasing operations during outages.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.x, Spring Web MVC, Spring JDBC, Spring Validation,
Spring Actuator, Jackson managed by Spring Boot, Flyway 12.x SQLite support, Xerial SQLite JDBC,
`io.github.erdtman:java-json-canonicalization` for RFC 8785 JCS, Picocli for vendor/customer CLIs,
Java `Signature`/`KeyPairGenerator` Ed25519 APIs

**Storage**: Dedicated SQLite 3.51.3 or newer database in WAL mode on local or block-backed
persistent storage; separate permission-restricted files for License Server private identity and
trusted-state checkpoint; no product application database access

**Testing**: JUnit 5, AssertJ, Spring Boot test support, temporary-file SQLite integration tests,
barrier/latch concurrency tests, local-process HTTP integration tests, Maven end-to-end acceptance
script

**Target Platform**: Local Java 21 process on Linux, macOS, or Windows for the POC; customer-managed
internal network; Tomcat client integration and Kubernetes identity behavior examples

**Project Type**: Maven multi-module libraries, one locally runnable REST service, vendor/customer
CLIs, examples, and runnable acceptance scripts

**Performance Goals**: p95 under 250 ms for online entitlement decisions and under 500 ms for
capacity-changing decisions at 50 requests/second on the documented reference host; cached product
and feature decisions under 25 ms; no oversubscription in 1,000 contested final-capacity trials

**Constraints**: Fully operational without Internet after artifacts are staged; one License Server
process and one SQLite writer; database cannot reside on NFS or another network filesystem; no
vendor private key in runtime dependencies or distributions; private installation keys never returned by
an API or included in routine database backups; all security decisions return stable reason codes

**Scale/Scope**: POC validation profile of up to 100 logical product installations, 1,000 named
assignments, 200 active human leases, 100 registered agents, 50 active agent leases, 10 meters, and
1 million audit events; larger sizing and multi-node HA require a later storage adapter and load
qualification

## Constitution Check

*GATE: Passed before Phase 0 and passed again after Phase 1 design.*

- **Trust/key isolation — PASS**: `license-issuer-cli` alone depends on vendor signing APIs. Runtime
  modules contain public verification and License Server signing ports, never vendor private keys.
- **Signed/layered validation — PASS**: Ed25519 uses Java-supported APIs; signed envelopes preserve
  exact canonical payload bytes; license, activation, subscription, environment, human, product,
  AI, usage, and trusted-time decisions remain separate.
- **Central activated authority — PASS**: one activated License Server and one customer-wide SQLite
  state store own all allocations; applications cannot enforce by parsing license JSON.
- **Stable installation identity — PASS**: server and product key pairs persist independently of
  hosts, pods, and processes; signed requests prove possession; mTLS can be layered later.
- **Transactional allocation/metering — PASS**: every capacity mutation begins an SQLite IMMEDIATE
  transaction, evaluates live rows, mutates state, appends audit, and commits atomically.
- **Entitlement separation — PASS**: typed model and engine services isolate environments, human
  licensing, products/features, agents, and generic meters.
- **Offline resilience — PASS**: vendor connectivity is absent at runtime; cached certificates are
  finite and operation-scoped; new allocations and POC offline AEU fail closed.
- **Persistence/audit/reasons — PASS**: SQLite persists authoritative state; a separate checkpoint
  detects ordinary stale restores; decisions share a typed reason catalog and audit contract.
- **Honest threat model — PASS**: documentation states software-only limits for complete VM clones,
  root control, clock manipulation, and full-state rollback; identity provider ports permit TPM/HSM.
- **Module/client boundaries — PASS**: modules match constitution boundaries and product code uses
  `license-client-java`, not storage or commercial-license representations.
- **Proof/testing/documentation — PASS**: negative, race, restart, corruption, outage, expiry, and
  rollback suites plus the ordered 40-step demo are required deliverables.

No constitution violation requires a complexity exception.

## Project Structure

### Documentation (this feature)

```text
specs/001-central-offline-license-server/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── openapi.yaml
│   ├── signed-envelope.schema.json
│   ├── commercial-license.schema.json
│   ├── activation-request.schema.json
│   ├── activation-certificate.schema.json
│   ├── cached-entitlement.schema.json
│   └── cli-contracts.md
└── tasks.md                         # generated by $speckit-tasks
```

### Source Code (repository root)

```text
offline-license-platform/
├── pom.xml
├── license-model/
│   └── src/{main,test}/java/
├── license-crypto/
│   └── src/{main,test}/java/
├── license-engine/
│   └── src/{main,test}/java/
├── license-storage/
│   ├── src/{main,test}/java/
│   └── src/main/resources/db/migration/
├── license-server/
│   └── src/{main,test}/java/
├── license-client-java/
│   └── src/{main,test}/java/
├── license-issuer-cli/
│   └── src/{main,test}/java/
├── license-testkit/
│   └── src/main/java/
├── examples/
│   ├── sample-product/
│   ├── tomcat-example/
│   └── kubernetes-example/
├── deployment/
│   └── local/
│       ├── application-local.yaml
│       └── README.md
├── samples/
│   ├── licenses/
│   └── activation/
├── scripts/
└── docs/
    ├── architecture.md
    ├── trust-model.md
    ├── threat-model.md
    ├── license-format.md
    ├── license-signing.md
    ├── offline-activation.md
    ├── license-server-identity.md
    ├── environment-registration.md
    ├── user-licensing.md
    ├── ai-licensing.md
    ├── cached-entitlements.md
    ├── failure-semantics.md
    ├── disaster-recovery.md
    ├── kubernetes.md
    ├── tomcat.md
    └── poc-demo.md
```

**Structure Decision**: Use one Maven reactor rooted at `offline-license-platform`. Domain and
runtime modules form a directed dependency graph: model → crypto verification/storage ports →
engine → storage/server/client. The issuer CLI receives a separate vendor-signing source set/API
that no runtime module may depend on; Maven Enforcer and architecture tests enforce this. Examples,
deployment files, scripts, and documentation remain outside reusable runtime modules.

## Component Responsibilities

- **license-model**: immutable records, validated identifiers, enums, reason codes, decisions,
  envelopes, and schema-neutral entitlement interfaces. It has no Spring, JDBC, or signing-secret
  dependency.
- **license-crypto**: RFC 8785 canonical bytes, envelope codecs, hashes/fingerprints, Ed25519 public
  verification, License Server signing, and separate vendor signing packages. Architecture tests
  prohibit engine/server/client imports of `CommercialLicenseSigner` and `ActivationSigner`.
- **license-engine**: `LicenseValidationService`, `InstanceRegistrationService`,
  `UserLicenseService`, `EntitlementService`, `AgentRegistryService`, `AgentLeaseService`,
  `MeteringService`, `AEUPolicy`, `TrustedTimeService`, `CachedEntitlementService`,
  `LicenseAvailabilityPolicy`, `AuditService`, and transactional repository ports.
- **license-storage**: SQLite JDBC repositories, Flyway migrations, IMMEDIATE transaction runner,
  online backup adapter, trusted checkpoint file, busy retry, and integrity diagnostics. No domain
  policy is encoded in SQL repositories beyond structural constraints and atomic conditional writes.
- **license-server**: Spring Boot composition, stable DTO mapping, runtime/admin controllers,
  validation/error envelope, admin bearer bootstrap, signed-instance request filter, replay
  protection, Actuator health/readiness, and redacted structured logging.
- **license-client-java**: typed status/product/feature/lease/agent/usage API, HTTPS connectivity,
  signed requests, pinned License Server public identity, cached-certificate verification,
  availability policy, and stable reason mapping.
- **license-issuer-cli**: vendor key generation, license issuance/inspection/verification, activation
  inspection/signing, and customer administration command adapters. Private-key paths are always
  explicit and vendor commands are packaged separately from customer runtime distributions.
- **license-testkit**: deterministic clock, key/artifact fixtures generated per test, barriers,
  temporary SQLite installations, corrupt/stale state builders, and decision assertions. It ships
  no reusable private key.

The server private identity is provided through `IdentityProvider`; the POC binds
`SoftwareIdentityProvider`, while TPM/HSM providers can later implement the same sign/public-key
contract. Controller methods never receive private key bytes.

## Local Runtime and Operational Design

- `scripts/run-local.sh` starts the License Server and sample product as separate Java processes,
  records PID/log files, and supplies distinct local configuration directories. `stop-local.sh`
  shuts them down without deleting persistent state.
- The License Server writes SQLite data under `.local/license-server/data` and identity/checkpoint
  material under `.local/license-server/identity`. Startup creates directories and fresh TLS/admin
  material only through an explicit setup command; no default credential is packaged.
- Local-process integration tests launch the built License Server JAR on an ephemeral port and use
  the real Java client over HTTPS. They do not require Docker or Testcontainers.
- Docker, Docker Compose, and Helm artifacts are deferred from this POC. The module/storage/health
  boundaries remain packaging-neutral so a later deployment phase can add them without changing
  licensing behavior.
- The Kubernetes example and documentation define a narrowly scoped bootstrap mechanism for one
  shared product identity and validate three simulated replicas against a local License Server.
  Runnable cluster packaging is deferred with Helm.
- Internal HTTPS accepts a customer-provided server certificate. The POC admin bearer secret is
  externally supplied; the API boundary can later delegate to enterprise authentication. Runtime
  request signing remains independent of that future admin mechanism.
- Metrics expose request latency/count by operation and reason, active lease/utilization gauges,
  SQLite busy retries/checkpoint age/WAL size, cache issuance, trusted-time warnings, and audit-write
  failures. Labels never contain user, session, agent, or license IDs.

## Phase 0: Research Decisions

Research is consolidated in `research.md`. All technical choices are resolved; there are no
remaining clarification markers. Key decisions are SQLite WAL with explicit writer serialization,
JCS canonical payload bytes inside a versioned signed envelope, Java Ed25519, signed instance
requests, separate identity/checkpoint files, Spring JDBC rather than ORM, and a storage port for
future HA.

## Phase 1: Design Outputs

- `data-model.md` defines typed domain records, relational tables, constraints, lifecycle state
  machines, transaction boundaries, and integrity/checkpoint behavior.
- `contracts/openapi.yaml` defines public runtime and administrative HTTP operations with a common
  decision envelope and signed-request headers.
- JSON Schemas define signed envelopes, licenses, activation artifacts, and cached entitlements.
- `contracts/cli-contracts.md` defines vendor and customer command inputs, outputs, exit codes, and
  secret-handling rules.
- `quickstart.md` is the air-gapped build/run and 40-step validation guide.

## Post-Design Constitution Re-check

Phase 1 preserves every pre-design gate. In particular, SQLite does not weaken atomicity: the
single-instance POC deliberately serializes writers at the database transaction boundary and tests
every final-slot race. SQLite is not presented as an HA database; the repository boundary and
contract tests isolate persistence so a future coordinated server-database adapter can replace it.
Cached and signed artifacts remain instance-bound, finite, and independently verifiable. No vendor
secret appears in any runtime artifact, sample, distribution, schema, or quickstart command.

## Complexity Tracking

No constitutional violations or unjustified complexity are present.
