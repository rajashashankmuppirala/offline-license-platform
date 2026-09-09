<!--
Sync Impact Report
- Version change: scaffold (unratified) -> 1.0.0
- Modified principles:
  - Placeholder Principle 1 -> I. Explicit Trust Boundaries and Key Isolation
  - Placeholder Principle 2 -> II. Signed Licenses and Layered Validation
  - Placeholder Principle 3 -> III. Central, Activated Customer Authority
  - Placeholder Principle 4 -> IV. Stable Cryptographic Installation Identity
  - Placeholder Principle 5 -> V. Transactional Allocation, Leasing, and Metering
- Added principles:
  - VI. Separate Human, Product, Environment, and AI Entitlements
  - VII. Offline Resilience with Explicit Failure Semantics
  - VIII. Persistent, Auditable, and Explainable Decisions
  - IX. Honest Threat Model and Evolution to Hardware Trust
  - X. Modular Architecture and Stable Client Contracts
  - XI. Proof-Oriented Testing and Complete Documentation
- Added sections:
  - Mandatory Domain and Runtime Rules
  - Engineering Workflow and Quality Gates
- Removed sections: none; scaffold sections were concretized and expanded.
- Follow-up TODOs: none.
-->
# Enterprise Offline Licensing Platform Constitution

## Core Principles

### I. Explicit Trust Boundaries and Key Isolation

The architecture MUST explicitly model five separate trust domains: the vendor trust domain, the
customer License Server, customer product installations, customer users, and customer
infrastructure administrators. The vendor private signing key MUST remain exclusively in the
vendor trust domain. It MUST NOT be committed to source control, embedded in code, images, Helm
charts, Kubernetes Secrets, WAR or JAR files, copied into customer environments, or required for
runtime validation. Customer environments MUST receive only public verification material.

Vendor signing and customer runtime verification MUST be separate modules. Runtime modules MUST
depend only on public verification APIs. Installation private keys, cryptographic seed material,
and authentication secrets MUST likewise never be logged or exported. These boundaries limit the
impact of a compromised customer system and prevent runtime components from gaining license-issuer
authority.

### II. Signed Licenses and Layered Validation

Every commercial license MUST be digitally signed with a maintained asymmetric implementation.
Ed25519 MUST be used unless a documented interoperability requirement justifies another modern
algorithm. Cryptographic primitives MUST NOT be implemented manually. Authenticity and integrity,
not encryption, are the security properties that prevent license modification; encryption MAY be
added only for confidentiality.

Validation MUST independently evaluate and return explicit outcomes for cryptographic validity,
activation validity, subscription validity, environment entitlement, human entitlement, product
entitlement, AI entitlement, and usage accounting. It MUST reject invalid signatures, modified or
malformed payloads, unsupported schema versions, expired or not-yet-valid licenses, locally known
revocations, and licenses bound to another License Server. Entitlement schema evolution MUST NOT
require cryptographic code changes.

### III. Central, Activated Customer Authority

The primary architecture MUST be a customer-hosted central License Server that is authoritative
for license validity, deployment and environment entitlement, human and agent allocation, product
and feature entitlement, AI metering, runtime decisions, and audit data. All installations in a
customer estate MUST coordinate through it over the customer's internal network. Vendor
connectivity MUST NOT be required at runtime.

The License Server MUST own customer-wide pools. Limits MUST NOT reset per environment: PROD, UAT,
DEV, and other installations consume the same pool unless the commercial license explicitly
defines separate pools. On first startup, the server MUST generate a stable licenseServerId, an
asymmetric installation key pair, a creation timestamp, and installation metadata. Its private key
MUST remain local.

An offline activation request MUST contain the installation public key and MUST NOT contain its
private key. The vendor-issued Activation Certificate MUST bind the commercial license ID,
customer ID, licenseServerId, License Server public-key fingerprint, activation identifier,
permitted activation policy, and issue timestamp. A license requiring activation MUST remain
invalid without a valid certificate, and copying only its license file to another server MUST NOT
activate that server.

### IV. Stable Cryptographic Installation Identity

Every logical product installation MUST have a stable installation identity and MUST register with
the License Server. Identity MUST NOT rely solely on an IP address, hostname, MAC address,
Kubernetes pod ID, or container ID. Registrations SHOULD use an installation-generated asymmetric
key pair because cryptographic proof survives ordinary runtime topology changes while resisting
identity spoofing. The License Server SHOULD authenticate product instances cryptographically, and
the protocol MUST permit internal HTTPS and future mTLS.

Environment allocation MUST apply to logical installations, not processes or replicas. Kubernetes
replica scaling, pod restart or rescheduling, and Tomcat restart MUST NOT consume a new environment
entitlement. Registration and active consumption MUST be distinct states so a licensed active
production installation and a registered passive disaster-recovery installation can coexist and
DR can be promoted without Internet access.

### V. Transactional Allocation, Leasing, and Metering

Concurrent human sessions and concurrent agent executions MUST use finite, renewable leases rather
than increment/decrement counters. A user lease MUST identify leaseId, userId, product instance,
sessionId, acquiredAt, heartbeatAt, and expiresAt. An agent lease MUST identify leaseId, agentId,
executionId, product instance, acquiredAt, heartbeatAt, and expiresAt. Expired leases MUST cease to
consume capacity without manual release.

Capacity checks and lease creation MUST be one atomic transaction. Usage counters and consumption
MUST also be transactional, so concurrent requests can never exceed the licensed limit. The first
accounting period MUST be MONTHLY, while the design MUST allow more period types. Agent or client
crashes MUST recover capacity through lease expiry, and all restart-sensitive state MUST persist.

### VI. Separate Human, Product, Environment, and AI Entitlements

Critical concepts MUST use explicit domain objects, never arbitrary maps. Human licensing MUST
independently support named users, concurrent users, role or category limits, and per-product user
limits. Named users MUST have explicit assignments; application user-table size MUST NOT become a
licensed-user count. Service and system accounts MUST be distinguishable from licensed humans.

Products and features MUST be first-class entitlements exposed through a stable entitlement API.
Product business code MUST NOT parse commercial license JSON. Environment types MUST include
PRODUCTION, DISASTER_RECOVERY, UAT, QA, DEVELOPMENT, and OTHER, with explicit per-type limits.
DR policy, including active/passive status and promotion rules, MUST be explicit and testable.

AI licensing MUST be independent of human-seat licensing. It MUST support AI enablement, registered
agent limits, concurrent agent execution limits, AI capability entitlements, and usage limits. An
agent MUST NOT consume a human license unless commercial policy explicitly says so. The initial
commercial unit MUST be AI_EXECUTION_UNIT (AEU), and its weighting MUST be centralized product
policy rather than logic scattered through agents. Metering MUST be extensible to tokens,
executions, workflows, tool calls, records, compute minutes, and other commercial metrics; raw
model tokens MUST NOT be the only licensing metric.

### VII. Offline Resilience with Explicit Failure Semantics

The system MUST distinguish loss of vendor connectivity from temporary unavailability of the
customer License Server. Loss of vendor connectivity MUST have no runtime impact. A temporary
License Server restart SHOULD NOT immediately disable existing production use because controlled,
bounded continuity is necessary for enterprise operations.

Each entitlement operation MUST document and test fail-open or fail-closed behavior.
Security-sensitive operations MUST default to fail-closed unless an explicit approved policy says
otherwise. Product clients MUST support finite signed cached entitlement capabilities bound to the
product installation and signed by the activated License Server identity. Cached capabilities MUST
state their permitted operations and expiry. Existing product use and ordinary entitlement checks
MAY use valid cached capability; new concurrent login MUST follow configured policy; new autonomous
AI execution SHOULD deny or receive only short grace due to its independent risk; usage-based AI
MUST operate offline only when explicitly enabled with a bounded local usage buffer.

### VIII. Persistent, Auditable, and Explainable Decisions

License Server state MUST reside in transactional persistent storage and survive restart. It MUST
include server identity, the commercial license, activation certificate, instance registrations,
environment allocations, named-user assignments, user leases, agent registrations and leases,
meter usage, trusted-time state, and audit events. Correctness MUST NOT depend on process memory or
sticky sessions. A single authoritative server is acceptable for the initial POC, but storage and
protocol design MUST permit later high availability.

Every meaningful decision MUST be deterministic, centralized, testable, auditable, and explainable
with a stable machine-readable reason code. Audit records SHOULD identify timestamp, customer,
license ID, product instance, operation, entitlement, decision, reason code, and relevant usage
values because these fields support investigation without exposing secrets. Database encryption
alone MUST NOT be considered the licensing security boundary. Integrity-sensitive state SHOULD use
tamper-evident protection where appropriate because privileged customer administrators remain in
the threat model.

### IX. Honest Threat Model and Evolution to Hardware Trust

The implementation and documentation MUST state that purely software-based offline licensing
cannot perfectly prevent duplication or time manipulation by an attacker controlling root or
administrator access, VM snapshots, the hypervisor, binaries, the filesystem, or the database. The
POC MUST detect and prevent normal copying and casual cloning and MUST NOT claim perfect VM-clone
prevention.

The design MUST permit future TPM 2.0, HSM, or other hardware-backed non-exportable identities.
Subscription validation MUST maintain last-known trusted local time, detect substantial backward
wall-clock movement using a configured tolerance, and return an explicit rollback reason. These
controls raise the cost and visibility of tampering without making claims that software alone
cannot uphold.

### X. Modular Architecture and Stable Client Contracts

Business code MUST consume a stable license-client API and MUST NOT know license storage or license
JSON details. The protocol MUST support license status, instance registration, named assignment,
user lease acquisition, heartbeat, and release; product and feature checks; agent registration;
agent lease acquisition and heartbeat; usage consumption; and cached entitlement retrieval.
Communication MUST support internal HTTPS; production design SHOULD support mTLS to authenticate
both endpoints.

The system SHOULD separate license-model, license-crypto, license-engine, license-storage,
license-server, license-client-java, license-issuer-cli, and license-testkit modules because these
boundaries enforce key isolation and independently testable contracts. Vendor signing capability
MUST never enter customer-facing runtime dependency graphs.

### XI. Proof-Oriented Testing and Complete Documentation

All important licensing behavior MUST have automated tests that assert the required outcome rather
than merely execute code. Security and correctness tests MUST cover modified payloads and
signatures, wrong activation certificates, cross-server copying, expired and future licenses,
unsupported schemas, environment exhaustion and duplicate production, Kubernetes replica scaling,
named-user exhaustion, user and agent concurrency races, expired leases, disabled AI, agent
registration exhaustion, usage boundaries, clock rollback, persistent-state corruption, server
restart and outage, and cached-entitlement expiry.

Documentation MUST cover architecture, trust boundaries, threat model, license format, offline
activation, License Server deployment, Tomcat and Kubernetes integration, environment registration,
human and AI licensing, failure behavior, disaster recovery, and known limitations. No placeholder
security logic or TODO-based implementation can satisfy a requirement.

## Mandatory Domain and Runtime Rules

- Commercial license configuration MUST support explicit limits for all required environment
  types. For example, one PRODUCTION, one DISASTER_RECOVERY, two UAT, two QA, and five DEVELOPMENT
  allocations are independently testable constraints.
- Registration MUST be separate from active license consumption. A passive DR instance MUST consume
  only what its explicit commercial policy defines, while promotion MUST atomically enforce the
  active production limit.
- A named-user assignment MUST identify a licensed human and applicable product or category. A user
  lease MUST reference a registered, authenticated installation and a unique session.
- Customer-wide pools MAY contain, for example, 500 named users, 20 concurrent users, 10 registered
  agents, three concurrent agents, and 100000 AEU per month. All environments MUST coordinate
  against the same authoritative values unless separate pools are explicit license data.
- Usage consumption MUST use compare-and-commit semantics: at 99998 of 100000 AEU, consuming two
  succeeds and the subsequent consumption of one is denied.
- Cached entitlement issuance MUST be auditable. Cache validity MUST never exceed its configured
  finite lifetime, and expired or disallowed cached operations MUST fail with their exact reason.
- Revocation MUST be enforced whenever trustworthy revocation information exists locally. The
  absence of vendor connectivity MUST NOT be represented as successful revocation checking.
- The stable reason-code catalog MUST include at least:
  - LICENSE_VALID
  - LICENSE_SIGNATURE_INVALID
  - LICENSE_FORMAT_INVALID
  - LICENSE_SCHEMA_UNSUPPORTED
  - LICENSE_NOT_YET_VALID
  - LICENSE_EXPIRED
  - LICENSE_GRACE_PERIOD
  - LICENSE_SERVER_NOT_ACTIVATED
  - LICENSE_SERVER_IDENTITY_MISMATCH
  - ENVIRONMENT_LIMIT_EXCEEDED
  - ENVIRONMENT_NOT_ENTITLED
  - INSTANCE_NOT_REGISTERED
  - INSTANCE_AUTHENTICATION_FAILED
  - ACTIVE_PRODUCTION_LIMIT_EXCEEDED
  - NAMED_USER_LIMIT_EXCEEDED
  - CONCURRENT_USER_LIMIT_EXCEEDED
  - USER_LEASE_INVALID
  - PRODUCT_NOT_ENTITLED
  - FEATURE_NOT_ENTITLED
  - PRODUCT_USER_LIMIT_EXCEEDED
  - AI_NOT_ENTITLED
  - AGENT_REGISTRATION_LIMIT_EXCEEDED
  - AGENT_CONCURRENCY_LIMIT_EXCEEDED
  - AI_USAGE_LIMIT_EXCEEDED
  - CLOCK_ROLLBACK_DETECTED
  - LICENSE_SERVER_UNAVAILABLE
  - CACHED_ENTITLEMENT_EXPIRED
  - CACHED_OPERATION_NOT_ALLOWED
  - STATE_INTEGRITY_FAILURE

## Engineering Workflow and Quality Gates

Every specification, plan, task list, implementation, and review MUST trace applicable work to
these principles. Architecture review MUST verify trust boundaries, key isolation, authoritative
pool scope, transaction boundaries, persistence, failure semantics, reason codes, audit coverage,
and known limitations before implementation is accepted.

Every entitlement operation MUST define its inputs, atomicity boundary, persistence behavior,
decision and reason code, audit event, outage policy, and tests. Security-sensitive changes MUST
include negative and tamper tests. Concurrency-sensitive changes MUST include deterministic race or
contention tests. Persistence-sensitive changes MUST include restart and corruption tests.

Reviews MUST reject manual cryptography, secret-bearing logs, runtime issuer dependencies,
in-memory authoritative state, counter-only concurrency, ambiguous instance identity, per-replica
environment consumption, silent fail-open behavior, raw-token-only AI policy, placeholder security
logic, and unproven claims of clone or clock-tamper prevention. Exceptions require a constitution
amendment or an explicit, time-bounded deviation approved and recorded under Governance.

## Governance

This constitution is the highest project engineering authority. Specifications, plans, tasks,
implementation choices, review conventions, and operational documentation MUST conform to it. When
another project artifact conflicts with this constitution, this constitution governs.

Amendments MUST be proposed as a documented change that states the rationale, security and
operational impact, migration or compatibility plan, affected principles, and updated test and
documentation obligations. Approval MUST include the product owner and the designated security or
architecture reviewer. Emergency deviations MUST be documented with owner, scope, risk, compensating
controls, expiry date, and remediation plan; they MUST NOT silently redefine a principle.

Constitution versions follow semantic versioning. MAJOR increments remove or incompatibly redefine
governance obligations; MINOR increments add principles or materially expand requirements; PATCH
increments clarify wording without changing obligations. The Sync Impact Report MUST describe each
change and the chosen bump.

Compliance MUST be reviewed during specification, design, implementation review, and release
readiness. Each release MUST have evidence for applicable quality gates, including tests and
required documentation. Reviewers MUST record any deviation and block release when mandatory
security, correctness, auditability, or documentation evidence is absent.

**Version**: 1.0.0 | **Ratified**: 2026-09-07 | **Last Amended**: 2026-09-07
