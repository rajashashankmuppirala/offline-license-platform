# Data Model: Central Offline License Server

## Modeling Rules

- Domain types are immutable Java records or final value objects validated at construction.
- Identifiers are nonblank canonical strings or UUIDs; enums reject unknown values unless a schema
  version explicitly defines forward-compatible handling.
- Instants are UTC and serialized as RFC 3339 strings; SQLite persists epoch milliseconds as
  INTEGER. Monthly meter periods use `[startInclusive, endExclusive)` UTC boundaries.
- Counts and AEU amounts are nonnegative 64-bit integers. Consumption amount must be positive and
  uses checked arithmetic.
- Signed artifacts are verified before payload binding. Product code never receives raw commercial
  license JSON or persistence records.
- All mutable rows carry `created_at`, `updated_at`, and an integer `row_version` where optimistic
  conflict reporting is useful; capacity correctness relies on an IMMEDIATE write transaction.

## Signed Envelope

**SignedEnvelope** fields:

- `envelopeVersion`: protocol version, initially `1`.
- `artifactType`: `COMMERCIAL_LICENSE`, `ACTIVATION_CERTIFICATE`, or `CACHED_ENTITLEMENT`.
- `payloadSchemaVersion`: schema understood independently from envelope and crypto versions.
- `algorithm`: exactly `Ed25519` for v1.
- `keyId`: SHA-256 fingerprint of the expected signing public key.
- `payload`: Base64URL without padding of RFC 8785 canonical JSON bytes.
- `signature`: Base64URL without padding of Ed25519 signature over a domain-separation prefix plus
  envelope metadata and exact payload bytes.

The signed input is length-prefixed UTF-8 fields in fixed order:
`offline-license-envelope-v1`, `artifactType`, `payloadSchemaVersion`, `algorithm`, `keyId`, and
`payload`. Length-prefix encoding is part of envelope v1 and prevents concatenation ambiguity.

## Commercial Entitlement Types

### CommercialLicense

- `schemaVersion: LicenseSchemaVersion`
- `licenseId: LicenseId`
- `customer: CustomerIdentity`
- `subscription: SubscriptionEntitlement`
- `activation: ActivationPolicy`
- `environments: List<EnvironmentEntitlement>` represented by a typed collection
- `users: UserEntitlement`
- `products: List<ProductEntitlement>` with unique product IDs
- `ai: AIEntitlement`
- `issuedAt: Instant`
- `licenseSequence: long`, monotonically increasing for renewal of the same license lineage
- `activeProductionClassLimit: int`
- `revocationIds: Set<String>`

### Supporting Immutable Types

- **LicenseSchemaVersion**: supported major/minor pair; unsupported major is rejected.
- **CustomerIdentity**: `customerId`, `companyName`, `contactEmail`.
- **SubscriptionEntitlement**: `type`, `tier`, `validFrom`, `validUntil`, `gracePeriodDays` (default
  7); `validFrom < validUntil` and grace is nonnegative.
- **ActivationPolicy**: `required`, `maxLicenseServers`, `renewalMayReuseActivation`.
- **EnvironmentType**: `PRODUCTION`, `DISASTER_RECOVERY`, `UAT`, `QA`, `DEVELOPMENT`, `OTHER`.
- **EnvironmentEntitlement**: `type`, `maxInstances`, `activePassive`, and optional `poolId`; the
  environment pool applies to user and agent capacity unless a meter explicitly overrides it.
- **UserEntitlement**: `maxNamedUsers`, `maxConcurrentUsers`, and category entitlements.
- **UserCategoryEntitlement**: category ID and maximum assignments.
- **ProductEntitlement**: product ID, enabled flag, optional max users, unique feature set.
- **FeatureEntitlement**: typed product ID and feature ID pair.
- **AIEntitlement**: enabled flag, max registered agents, max concurrent executions, meter list,
  and AI feature set.
- **MeterEntitlement**: meter ID, `MeterPeriod`, limit, optional pool ID.
- **MeterPeriod**: initially `MONTHLY`; period derivation always uses UTC.

Current-license compatibility is handled by an importer that maps legacy fields into these types;
the signed v1 model itself has no arbitrary entitlement maps.

## Activation and Server Identity

### LicenseServerIdentity

- `licenseServerId: UUID`
- `publicKey: SubjectPublicKeyInfoBytes`
- `publicKeyFingerprint: KeyFingerprint`
- `privateKeyReference: PathReference` (never the key bytes in domain/API/database projections)
- `createdAt: Instant`
- `providerType: SOFTWARE` initially; `TPM` and `HSM` are reserved provider capabilities
- `identityState: UNACTIVATED | ACTIVE | RECOVERY_REQUIRED`

### ActivationRequest

- `schemaVersion`, `requestId`, `licenseServerId`, installation public key and fingerprint,
  `licenseId`, `customerId`, random nonce, `createdAt`, and optional typed environment evidence.
- The request is not an authorization. It contains no private key.

### ActivationCertificate

- `schemaVersion`, `activationId`, `licenseId`, `customerId`, `licenseServerId`, public-key
  fingerprint, copied activation policy, `issuedAt`, optional validity bounds, and license sequence
  range or lineage allowed for renewal.
- The certificate is carried in a vendor-signed envelope.

## Product Installation and Environment

### RegisteredProductInstance

- `instanceId: UUID`
- `customerId`, `productId`, `environmentType`, `deploymentType: TOMCAT | KUBERNETES | OTHER`
- `publicKey`, `publicKeyFingerprint`, `applicationVersion`
- `instanceState: InstanceState` (`REGISTERED | DECOMMISSIONED`)
- `environmentState: EnvironmentState` (`INACTIVE | STANDBY | ACTIVE`)
- `registeredAt`, `lastAuthenticatedAt`, `decommissionedAt`, and optional metadata allowlist
- `registrationGeneration`, incremented only by an authorized identity lifecycle action

The external lifecycle view is derived as DECOMMISSIONED when `instanceState` is DECOMMISSIONED,
ACTIVE or STANDBY from `environmentState`, and REGISTERED when the environment state is INACTIVE.

One Kubernetes installation has exactly one instance ID/key pair shared by all replicas. Namespace
restoration with the same key resumes the record. A new key is a new installation and cannot claim
the old row.

### EnvironmentAllocation

- `allocationId`, `instanceId`, `environmentType`, `poolId`
- `registeredConsumption: boolean`
- `productionClassActive: boolean`
- `allocatedAt`, `releasedAt`

An instance has at most one unreleased allocation. Registration consumption and production-class
activity are separate to support PROD ACTIVE plus DR STANDBY.

### Instance State Transitions

```text
REGISTERED/INACTIVE ──activate(non-DR)─────> REGISTERED/ACTIVE
REGISTERED/INACTIVE ──mark-standby(DR)────> REGISTERED/STANDBY
REGISTERED/STANDBY ──atomic promotion─────> REGISTERED/ACTIVE
REGISTERED/ACTIVE ──atomic demotion───────> REGISTERED/STANDBY (DR)
REGISTERED/* ──decommission───────────────> DECOMMISSIONED/INACTIVE
DECOMMISSIONED/INACTIVE ──no transition───> terminal
```

Normal decommission requires no live leases. Force decommission revokes leases and centrally
revokes cached issues, emits a residual-offline warning, releases the allocation, and tombstones
the identity. Previously issued offline certificates remain usable only until their bounded expiry.

## Human Licensing

### NamedUserAssignment

- `assignmentId`, `customerId`, canonical `userId`, optional product ID and category ID
- `assignedAt`, `releasedAt`, `cooldownUntil`
- `overrideType: NONE | EMPLOYEE_DEPARTURE | CORRECTION`
- override actor, reason, previous user, and replacement user when bypassing cooldown

Only rows with `releasedAt = null` count as active assignments. Released capacity cannot be
reassigned before `cooldownUntil` without a complete audited override.

### ConcurrentUserLease

- `leaseId`, `customerId`, `userId`, `instanceId`, `sessionId`, `poolId`
- `acquiredAt`, `heartbeatAt`, `expiresAt`, `releasedAt`
- `status: ACTIVE | RELEASED | EXPIRED | REVOKED`
- idempotency key unique per instance and acquisition request

Default duration is five minutes and expected heartbeat is one minute. A lease counts only when it
is ACTIVE, not released, and `expiresAt > decisionTime`. Persisted absolute expiry is never extended
by server downtime. Cleanup updates status for diagnostics but is not needed for counts.

## AI Licensing and Metering

### RegisteredAgent

- `agentId`, `displayName`, `instanceId`, `registeredAt`, `status: ACTIVE | DECOMMISSIONED`
- unique agent ID within the customer; active rows count against the global pool

### AgentExecutionLease

- `leaseId`, `agentId`, `executionId`, `instanceId`, `poolId`
- `acquiredAt`, `heartbeatAt`, `expiresAt`, `releasedAt`
- `status: ACTIVE | RELEASED | EXPIRED | REVOKED`
- idempotency key unique per execution acquisition request

It uses the same five-minute/one-minute defaults and live-expiry counting rule as user leases.

### UsageConsumption

- `consumptionId`, `idempotencyKey`, `instanceId`, `meterId`, `amount`
- allowlisted string dimensions with bounded key/value lengths
- derived `periodStart`, `periodEnd`, `requestedAt`
- `decision`, `reasonCode`, and `usageAfter` when allowed

### UsageCounter

- composite identity: customer, license lineage, pool, meter ID, period start
- `periodEnd`, `limitAmount`, `consumedAmount`, `rowVersion`, `updatedAt`

Consumption is idempotent. Unused AEU never rolls over. Renewal of the same customer/license lineage
retains the current-period counter; a lower new limit can block new use but never erases history.

### AEUPolicy

The policy is not a meter row. It maps product action IDs to amounts, initially:

- `INTERACTIVE_AI_REQUEST = 1`
- `AUTONOMOUS_AGENT_RUN = 5`
- `AGENT_TOOL_INVOCATION = 1`
- `SCHEDULED_AGENT_RUN = 5`

The generic metering service accepts only the resulting meter ID, amount, and dimensions.

## Cached Entitlements and Availability

### CachedEntitlementCertificate

- `schemaVersion`, `certificateId`, `licenseId`, `licenseServerId`, `instanceId`
- instance public-key fingerprint, `issuedAt`, `expiresAt`
- product entitlements and feature sets allowed offline
- typed `OfflineOperationPolicy`
- server identity `keyId`

Expiry is at most 24 hours and the earliest of configured cache expiry, subscription/grace expiry,
or any earlier entitlement boundary. It is signed by the License Server identity. Central state
tracks issuance and revocation, but a disconnected verifier can observe only signed content and
time; force-decommission therefore cannot shorten a certificate already offline.

### LicenseAvailabilityPolicy

- Cache allowed: `PRODUCT_CHECK`, `FEATURE_CHECK`, and existing product operation.
- Central required: `NEW_USER_LOGIN`, `NAMED_USER_ASSIGNMENT`, `ENVIRONMENT_REGISTRATION`,
  `AGENT_REGISTRATION`, and `USAGE_CONSUMPTION`.
- `AGENT_EXECUTION` requires central authority unless its existing lease is still unexpired.
- Offline AEU allowance is zero for the POC.

## Decisions, Reasons, and Audit

### LicenseDecision

- `decisionId`, `allowed`, `reasonCode`, `decidedAt`, `operation`, `subject`, optional entitlement
  and utilization values, warnings, and correlation ID.
- Success and denial use the same envelope. HTTP status does not replace the reason code.

### LicenseReasonCode

A closed enum contains every constitution/spec reason plus clarified
`NAMED_USER_REASSIGNMENT_COOLDOWN` and `STATE_ROLLBACK_DETECTED`. Unknown response codes received by
an older client map to `UNKNOWN_LICENSE_DECISION` while preserving the raw code for diagnostics.

### AuditEvent

- `auditId`, `occurredAt`, customer ID, license ID, instance ID when applicable
- actor type and actor ID, operation, entitlement, decision, reason code
- bounded non-secret before/after utilization values, correlation ID
- `previousEventHash`, `eventHash` for tamper evidence

Heartbeat success is not audited by default; denial, invalid signature, activation, import,
lifecycle, assignment, lease denial/revocation, meter denial, rollback, and cached use are audited.

## Trusted Time and Integrity

### TrustedTimeState

- singleton ID, `highestAcceptedTime`, `stateSequence`, `updatedAt`, `rowVersion`
- every meaningful committed mutation increments `stateSequence`

### External Trusted Checkpoint

- `licenseServerId`, `stateSequence`, `highestAcceptedTime`, `databaseDigestReference`, `writtenAt`
- signed by the License Server identity and stored outside routine SQLite backup

At startup, a database sequence lower than a valid local checkpoint enters RECOVERY_REQUIRED and
denies new allocations, consumption, and cache issuance. Clock movement more than five minutes
behind highest accepted time returns CLOCK_ROLLBACK_DETECTED. Complete loss of database, identity,
and checkpoint creates a new UNACTIVATED installation. This does not claim protection against a
complete privileged rollback of every file or VM.

## Relational Schema

Flyway creates these SQLite tables:

- `license_server_identity`: singleton public identity metadata, provider, state, and key reference.
- `commercial_license`: immutable signed envelope, parsed identity/validity projection, sequence,
  active flag, and import audit reference.
- `activation_certificate`: immutable signed envelope and indexed binding projection.
- `product_instance`: identity, product/deployment/environment, lifecycle, tombstone, timestamps.
- `environment_allocation`: instance allocation, registered and active consumption, release.
- `named_user_assignment`: active/released assignment and cooldown/override evidence.
- `user_lease`: session lease, absolute expiry, release/revocation, idempotency.
- `registered_agent`: agent identity, owning instance, lifecycle.
- `agent_lease`: execution lease, absolute expiry, release/revocation, idempotency.
- `meter_definition`: active license meter projection and UTC period policy.
- `meter_usage`: one row per meter pool and UTC period.
- `usage_consumption`: immutable idempotent usage ledger.
- `trusted_time_state`: singleton high-water time and state sequence.
- `cached_entitlement_issue`: certificate envelope/hash, binding, expiry, central revocation.
- `request_nonce`: instance, nonce, request timestamp, expiry for replay protection.
- `audit_event`: append-only decision/lifecycle ledger with hash chaining.

### Database Constraints and Indexes

- Primary and foreign keys on every identifier; `PRAGMA foreign_keys=ON` is verified at startup.
- Unique license sequence per license lineage and one active commercial license.
- Unique server identity singleton and activation ID.
- Unique instance public-key fingerprint and instance ID; decommissioned rows remain tombstones.
- Partial unique index for one unreleased environment allocation per instance.
- Unique active named assignment for customer/user/product/category scope.
- Unique lease acquisition and usage-consumption idempotency keys.
- Unique meter usage row for customer/license lineage/pool/meter/period start.
- Unique instance nonce within its replay window.
- Check constraints for nonnegative limits/usage and `consumedAmount <= limitAmount`.
- Check constraints for validity and lease/cache time ordering.

Licensed counts above one cannot be expressed by a unique constraint. They are enforced inside the
single serialized write transaction and proven by contention tests.

## Atomic Transaction Boundaries

Each boundary begins `BEGIN IMMEDIATE`, uses a bounded busy retry, and commits decision plus audit:

1. Instance register/decommission and environment allocation.
2. PROD/DR promote, demote, and failback as one two-instance transition.
3. Named assign/release/cooldown override.
4. User lease acquire/heartbeat/release/revoke.
5. Agent register/decommission and execution lease operations.
6. Meter consumption and immutable consumption-ledger insert.
7. License/activation import and renewal projection replacement.
8. Trusted-time/state-sequence advance and cache certificate issue record.

Read-only product/feature/status operations use a consistent read transaction. The separate signed
checkpoint is updated after commit; a crash between database commit and checkpoint write is safe
because the checkpoint may lag, never lead. Backup uses SQLite's online backup mechanism or
`VACUUM INTO`, then records its state sequence and excludes identity private-key material.
