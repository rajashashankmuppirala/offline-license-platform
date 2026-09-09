# Feature Specification: Central Offline License Server

**Feature Branch**: Not created; no branch hook is configured

**Created**: 2026-09-07

**Status**: Draft

**Input**: Enterprise POC for a customer-hosted central offline License Server that coordinates
commercial entitlements across air-gapped Tomcat and Kubernetes product installations.

## Clarifications

### Session 2026-09-07

- Q: What recovery policy should govern License Server backup, restoration, hardware migration, and
  complete VM cloning? → A: Keep the POC simple: restore only on the original installation using
  its existing local identity; new hardware requires a new identity and offline reactivation;
  advanced full-VM-clone detection is deferred.
- Q: What should define one Kubernetes logical product installation and authenticate it across pod
  scaling, restarts, and namespace recreation? → A: Automatically generate one installation key
  pair and stable instance ID during normal product setup, share it across replicas, and use signed
  requests over internal HTTPS for the POC; restored identity preserves the installation, while a
  lost identity creates a new installation; retain compatibility with future mTLS.
- Q: How should environment decommissioning, entitlement reuse, and PROD/DR failover and failback
  work? → A: Use REGISTERED, STANDBY, ACTIVE, and DECOMMISSIONED states; drain leases before normal
  decommission or use an audited force operation that centrally revokes leases and cached
  permissions; reuse capacity immediately; promote and fail back through atomic PROD/DR handoffs.
  A disconnected client may retain only the bounded permission in an already-issued certificate.
- Q: What default lease and outage policy should the POC enforce for crashed sessions and temporary
  License Server unavailability? → A: Use five-minute user and agent leases with one-minute
  heartbeats; persist unexpired leases across restart; permit only existing product sessions and
  feature checks with cached entitlements for at most 24 hours and never past license/grace expiry;
  deny new allocations and use zero offline AEU allowance.
- Q: Which default commercial-lifecycle policy should govern named-user reassignment, monthly AEU,
  renewal and grace, clock rollback, and restoration of an older database backup? → A: For the POC,
  use a 30-day named-seat reassignment cooldown with audited departure/correction override, no AEU
  rollover, renewal that preserves activation and current-period usage, seven-day restricted grace,
  five-minute rollback tolerance, recovery-required handling for stale database restores, and
  UNACTIVATED state after complete persistent-state deletion; these defaults may evolve later.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Issue and Activate a Bound Customer License (Priority: P1)

A Vendor Licensing Administrator issues a signed, versioned commercial license without exposing
the vendor signing secret. A Customer Infrastructure Administrator installs a License Server in an
air-gapped estate, exports its activation request, obtains an activation certificate through an
offline exchange, and imports both artifacts. The activated license is usable only by the License
Server identity named in the certificate.

**Why this priority**: An authentic license bound to one customer-hosted authority is the root of
all subsequent entitlement enforcement and directly prevents casual multiplication by copying.

**Independent Test**: Issue and activate a license on one server, alter the license, and copy the
original artifacts to a second independently initialized server. The first server becomes active;
the altered license and second-server import are rejected with precise reasons.

**Acceptance Scenarios**:

1. **Given** vendor signing material and A Inc. entitlement terms, **When** an administrator issues
   the master license, **Then** the artifact is signed, versioned, inspectable, and contains no
   signing secret.
2. **Given** a newly installed License Server, **When** it starts for the first time, **Then** it is
   UNACTIVATED and has a stable local identifier and installation public identity.
3. **Given** an unactivated server and matching commercial license, **When** an activation request
   is exported, **Then** it identifies the server, license, customer, nonce, creation time, and
   public-key fingerprint but contains no private key.
4. **Given** a valid request, **When** the vendor issues an activation certificate and the customer
   imports both signed artifacts, **Then** all signatures, identifiers, fingerprints, schema
   versions, and validity dates are checked before status becomes ACTIVE.
5. **Given** a valid license whose concurrent-user value is changed from 20 to 200 after issuance,
   **When** it is imported or evaluated, **Then** it is rejected as modified.
6. **Given** a second independently initialized server, **When** the first server's license and
   activation certificate are imported, **Then** activation is rejected for identity mismatch.
7. **Given** a renewal for the same customer and server identity, **When** policy permits renewal,
   **Then** the new license can be imported without regenerating the server identity.

---

### User Story 2 - Register and Govern Logical Environments (Priority: P1)

A Customer Product Administrator registers each logical installation and its environment type.
The License Server applies customer-wide environment quantities without counting Kubernetes pods
or application restarts as additional installations. Production and passive disaster recovery are
registered separately, and promotion preserves the licensed active-production limit.

**Why this priority**: Environment control closes the multiplication problem at deployment level
while supporting both target deployment models and offline disaster recovery.

**Independent Test**: Register one Kubernetes PROD, one UAT, and one standby DR installation;
reject a second PROD; scale and restart PROD without changing allocation; then exercise DR
promotion while proving that no more than one production-class installation is active.

**Acceptance Scenarios**:

1. **Given** a license allowing one PROD and two UAT instances, **When** one PROD and one UAT are
   registered, **Then** each consumes exactly one entitlement of its declared type.
2. **Given** the PROD allowance is exhausted, **When** another logical PROD is registered, **Then**
   registration fails with ENVIRONMENT_LIMIT_EXCEEDED.
3. **Given** a registered Kubernetes installation, **When** replicas scale from 3 to 20 or pods are
   restarted or rescheduled, **Then** the installation remains one allocation.
4. **Given** a Kubernetes namespace is deleted and recreated, **When** its backed-up installation
   identity is restored, **Then** it resumes the same registration; without that identity it is a
   new installation and cannot silently assume the old allocation.
5. **Given** a registered Tomcat installation, **When** Tomcat restarts with its stable identity,
   **Then** no new environment allocation is consumed.
6. **Given** one active PROD and a licensed passive DR registration, **When** DR requests promotion,
   **Then** promotion succeeds only if the resulting state contains at most one active
   production-class installation.
7. **Given** an instance with active leases, **When** normal decommissioning is requested, **Then**
   it remains registered until its leases drain; an explicit audited force operation terminates
   the leases and centrally revokes cached permissions before decommissioning.
8. **Given** an instance reaches DECOMMISSIONED, **When** another eligible instance registers,
   **Then** the released environment entitlement is immediately available.
9. **Given** DR is active after failover, **When** PROD is ready for failback, **Then** one atomic
   handoff places PROD in ACTIVE and DR in STANDBY without an overlap of active production-class
   consumption.

---

### User Story 3 - Enforce Shared Human Licensing (Priority: P1)

Customer Product Administrators assign named users and product applications obtain expiring
concurrent-user leases. All environments use one customer-wide pool unless the license explicitly
defines separate pools. Licensed role categories and product-specific capacities are enforced
independently.

**Why this priority**: A shared pool prevents a 20-user entitlement from becoming 60 slots across
PROD, UAT, and DEV and preserves existing named, reporting, and issue-manager business concepts.

**Independent Test**: Allocate leases across multiple environments until the global limit is met,
prove the next request is denied, allow one lease to expire, and prove capacity becomes available;
separately test named, role-category, and product limits.

**Acceptance Scenarios**:

1. **Given** a 20-concurrent-user pool with 12 PROD, 5 UAT, and 3 DEV leases, **When** any environment
   requests another lease, **Then** the request is denied with CONCURRENT_USER_LIMIT_EXCEEDED.
2. **Given** one remaining concurrent slot and two simultaneous requests, **When** both are
   evaluated, **Then** exactly one succeeds.
3. **Given** an application disappears without releasing a lease, **When** its lease expires,
   **Then** the lease stops counting within five minutes of its last successful heartbeat and
   another session can acquire capacity.
4. **Given** a global named-user limit, **When** assignments across installations reach it, **Then**
   the next assignment is denied; user-table rows without assignments do not consume licenses.
5. **Given** independent REPORTING and DQ_ISSUE_MANAGER limits, **When** either category reaches its
   own limit, **Then** only additional assignments in that category are denied.
6. **Given** a named assignment is released, **When** another user is assigned against that released
   capacity within 30 days, **Then** reassignment is denied unless an administrator supplies an
   audited employee-departure or correction override.

---

### User Story 4 - Check Products and Features (Priority: P2)

Product applications ask the License Server whether products and features are entitled without
interpreting commercial license documents. Initial products are VALIDATOR, DATA_MANAGER,
DATA_QUALITY, and DATA_FLOW. Initial Validator features are TABLEAU, ORACLE_ANALYTICS, and COGNOS.

**Why this priority**: A stable entitlement decision surface decouples applications from license
format evolution and supports the current product catalog.

**Independent Test**: Import an A Inc. license, check every enabled product and feature, and verify
that an absent feature and an over-capacity product user are denied with exact reason codes.

**Acceptance Scenarios**:

1. **Given** an active license containing VALIDATOR and TABLEAU, **When** the registered Validator
   installation checks them, **Then** both checks succeed.
2. **Given** a feature absent from the license, **When** it is checked, **Then** the result is denied
   with FEATURE_NOT_ENTITLED.
3. **Given** a product-specific user limit is exhausted, **When** another applicable user is added,
   **Then** the operation is denied with PRODUCT_USER_LIMIT_EXCEEDED.

---

### User Story 5 - License AI Agents Independently (Priority: P1)

An AI Agent Runtime uses AI-specific entitlements rather than human seats. Administrators register
agents up to a customer-wide limit, runtimes acquire expiring execution leases, and usage is
charged in monthly AI_EXECUTION_UNIT (AEU) quotas across all environments.

**Why this priority**: Autonomous consumption can grow while human usage falls; separate agent,
concurrency, capability, and usage controls are therefore a core commercial requirement.

**Independent Test**: Reach registered-agent and concurrent-execution limits, let an execution
lease expire, consume the monthly AEU allowance to its boundary, and prove all over-limit attempts
are denied without consuming human capacity.

**Acceptance Scenarios**:

1. **Given** AI is disabled, **When** an agent capability is requested, **Then** it is denied with
   AI_NOT_ENTITLED regardless of available human seats.
2. **Given** a maximum of 10 registered agents, **When** an eleventh agent is registered, **Then** it
   is denied with AGENT_REGISTRATION_LIMIT_EXCEEDED.
3. **Given** three active agent execution leases, **When** a fourth execution requests a lease,
   **Then** it is denied with AGENT_CONCURRENCY_LIMIT_EXCEEDED.
4. **Given** one remaining execution slot and simultaneous requests, **When** both are evaluated,
   **Then** exactly one receives a lease.
5. **Given** two remaining monthly AEU and two simultaneous requests to consume two AEU, **When**
   both are evaluated, **Then** exactly one succeeds and recorded use never exceeds the limit.

---

### User Story 6 - Continue Safely During a Server Outage (Priority: P1)

A Product Application holds a finite cached entitlement certificate signed by the activated
License Server and bound to that logical installation. During temporary server unavailability,
allowed existing operations continue for the configured grace interval while operations that
could increase customer-wide allocation remain denied or strictly bounded.

**Why this priority**: The central authority must prevent entitlement multiplication without making
customer production immediately dependent on one process being continuously available.

**Independent Test**: Issue a cached certificate, stop the License Server, prove existing product
and feature checks continue, prove new registrations and assignments fail, exhaust any explicit
offline AI allowance, and prove expiry ends all cached permission.

**Acceptance Scenarios**:

1. **Given** a valid cached certificate, **When** the License Server is unavailable, **Then** existing
   product operation and listed feature checks remain allowed until certificate expiry.
2. **Given** server unavailability, **When** a new named assignment, environment registration, or AI
   agent registration is attempted, **Then** it is denied.
3. **Given** server unavailability, **When** a new concurrent human login is attempted, **Then** it
   is denied by default unless explicit customer policy provides bounded capacity.
4. **Given** server unavailability, **When** an autonomous execution lacks a still-valid local
   execution lease, **Then** it is denied.
5. **Given** an explicit bounded offline AI allowance, **When** the allowance is exhausted, **Then**
   further centrally metered AI usage is denied.
6. **Given** an expired or instance-mismatched cached certificate, **When** any cached operation is
   attempted, **Then** it is denied with the corresponding stable reason code.
7. **Given** an existing session is using cached permission during an outage, **When** connectivity
   returns, **Then** it must resume heartbeats or reacquire central capacity and is denied if no
   capacity is available.

---

### User Story 7 - Operate, Diagnose, and Recover the License Service (Priority: P2)

Customer administrators and support engineers inspect license status, utilization, warnings, and
auditable decisions. Server restart preserves identity and all authoritative state. Subscription
expiry, configured grace, and significant backward clock movement produce explicit decisions
without vendor connectivity.

**Why this priority**: Air-gapped customers need self-contained operations and support evidence,
and enforcement must remain correct across ordinary failures and subscription lifecycle events.

**Independent Test**: Populate every allocation type, inspect status and audit views, restart the
server, compare state before and after, then exercise license expiry, grace, and clock rollback.

**Acceptance Scenarios**:

1. **Given** an active populated license, **When** an administrator views status and utilization,
   **Then** customer, license, validity, activation, environment use, human use, product and feature
   entitlement, agent use, AI usage, grace, and warnings are visible.
2. **Given** meaningful allow and deny decisions, **When** support inspects audit history, **Then**
   each record includes its operation, result, reason, applicable identity, and usage context but no
   secret material.
3. **Given** active state, **When** the License Server restarts, **Then** identity, activation,
   registrations, assignments, metering, and still-relevant leases remain correct.
4. **Given** a subscription reaches its end and any configured grace also ends, **When** a licensed
   capability is requested, **Then** it is denied without contacting the vendor.
5. **Given** wall-clock time moves backward beyond tolerance, **When** validity is evaluated,
   **Then** the decision is CLOCK_ROLLBACK_DETECTED and the event is auditable.
6. **Given** a database backup older than the latest local trusted checkpoint is restored, **When**
   the server starts, **Then** it enters RECOVERY_REQUIRED and denies new allocations until an
   administrator completes an audited reconciliation.
7. **Given** all persistent License Server state is deleted, **When** the service initializes again,
   **Then** it creates a new identity and starts UNACTIVATED rather than reconstructing authority.

### Edge Cases

- A license or activation artifact is malformed, uses an unsupported schema, has a bad signature,
  is not yet valid, is expired, is locally revoked, or identifies a different customer or license.
- Activation request or certificate fields are individually changed, replayed with an old nonce, or
  paired with a different commercial license.
- A server identity store is missing or corrupted after prior activation.
- Two identical instance registration requests arrive concurrently, or one identity attempts to
  change its product, deployment type, or environment without an authorized transition.
- A Kubernetes rollout temporarily runs old and new pods using the same logical identity.
- Instance decommission is requested while human leases, agent leases, or cached certificates still
  reference it; normal decommission waits, while an explicit force operation revokes them first.
- A force-decommissioned instance remains disconnected and presents a previously issued cached
  certificate; it may continue only through that certificate's bounded validity, and this residual
  software-only limitation is visible to the administrator before force decommission is confirmed.
- PROD demotion and DR promotion overlap, fail midway, or arrive concurrently.
- Heartbeats arrive at, just before, or just after lease expiry; delayed heartbeats target leases
  that have already been replaced.
- Named assignments overlap across products or role categories, and service accounts are presented
  as human users.
- Monthly metering crosses an accounting boundary while usage requests are in flight.
- A renewal reduces limits below current allocations, removes a currently used entitlement, or is
  imported midway through an accounting period; current-period usage is retained.
- Cached certificate expiry, license expiry, grace expiry, and trusted-time rollback overlap.
- Persistent state is partially corrupted or cannot pass integrity checks after restart.
- Audit history grows while the estate remains permanently air-gapped.

### Acceptance Demonstration

The runnable POC demonstration MUST execute this ordered, self-contained journey and assert each
stated outcome:

1. Generate a vendor key pair.
2. Issue the A Inc. master license.
3. Start the first License Server.
4. Verify the server is UNACTIVATED.
5. Generate its activation request.
6. Issue an activation certificate from that request using vendor-only operations.
7. Import the commercial license and activation certificate.
8. Verify the first License Server becomes ACTIVE.
9. Change the issued concurrent-user value from 20 to 200 without reissuing the signature.
10. Verify the modified license is rejected.
11. Start a second, independently initialized License Server.
12. Import the first server's license and activation certificate into the second server and verify
    rejection for identity mismatch.
13. Register a PROD Kubernetes logical instance.
14. Register a UAT logical instance.
15. Attempt a second PROD registration when the maximum is one.
16. Verify the second PROD is rejected.
17. Restart or replace the PROD pods.
18. Verify the PROD deployment still consumes exactly one environment allocation.
19. Acquire 20 concurrent-user leases across more than one environment.
20. Attempt lease 21.
21. Verify lease 21 is rejected.
22. Expire one lease.
23. Acquire a replacement and verify capacity recovery.
24. Assign named users up to the configured limit.
25. Verify the next named assignment is rejected.
26. Check an unlicensed feature and verify rejection.
27. Register AI agents up to the configured limit.
28. Attempt one more agent registration and verify rejection.
29. Acquire agent execution leases up to the configured concurrency limit.
30. Attempt one more simultaneous execution and verify rejection.
31. Consume AEU from the customer-wide monthly meter.
32. Reach the AEU quota exactly.
33. Attempt additional AEU consumption and verify rejection.
34. Obtain a cached entitlement certificate, then stop the License Server.
35. Verify an entitled product feature remains available through the valid cached certificate.
36. Verify a sensitive operation, including new environment registration, cannot be completed
    during the outage.
37. Restart the License Server.
38. Verify persistent authoritative state matches the state before shutdown.
39. Advance through subscription expiry and configured grace and verify final denial.
40. Move the wall clock backward beyond tolerance and verify CLOCK_ROLLBACK_DETECTED.

## Requirements *(mandatory)*

### Functional Requirements

#### Commercial License and Trust

- **FR-001**: The system MUST provide a versioned commercial entitlement model containing license
  and customer identifiers, customer contact details, subscription terms, activation policy,
  environment limits, human entitlements, products, features, AI entitlements, and usage meters.
- **FR-002**: The model MUST retain the business meaning of companyName, contactEmail, licenseType,
  startDate, endDate, subscriptionType, concurrentUser, totalNamedUsers,
  totalReportingUsers, totalDQIssueManagerUsers, productCollection, product user limits, and
  sub-products from the current license.
- **FR-003**: The model MUST map current product names to stable identifiers: VALIDATOR,
  DATA_MANAGER, DATA_QUALITY, and DATA_FLOW, and current Validator sub-products to TABLEAU,
  ORACLE_ANALYTICS, and COGNOS.
- **FR-004**: A commercial license MUST be asymmetrically signed and independently verifiable using
  public material; changing any protected entitlement MUST invalidate it.
- **FR-004A**: Every signed artifact MUST use the documented RFC 8785 JSON Canonicalization Scheme
  over the payload, combined with a versioned artifact-type domain separator. Verification MUST
  reject a different canonicalization version, artifact type, malformed payload, or signature.
- **FR-005**: Vendor private signing material MUST remain solely with vendor issuance operations and
  MUST never be required by the License Server, product applications, customer deployment
  artifacts, or cached-entitlement verification.
- **FR-006**: License evaluation MUST reject malformed, modified, unsupported, not-yet-valid,
  expired, locally revoked, and incorrectly bound artifacts with stable reasons.
- **FR-007**: Subscription terms MUST support a start, end, tier, and nonnegative grace interval,
  with expiration enforced entirely inside the customer estate. The POC default grace period MUST
  be seven days.
- **FR-007A**: During subscription grace, only product instances, named assignments, users with a
  prior successful licensed session, and agents registered before license expiry MAY continue
  within existing limits. New instance, named-user, and agent registrations MUST be denied, and
  every allowed decision MUST return LICENSE_GRACE_PERIOD.
- **FR-008**: Activation policy MUST state whether activation is required and the permitted number
  of License Server activations.
- **FR-009**: Environment terms MUST independently limit PRODUCTION, DISASTER_RECOVERY, UAT, QA,
  DEVELOPMENT, and OTHER instances.
- **FR-010**: Human terms MUST independently express global named, concurrent, category, and
  per-product user limits.
- **FR-011**: AI terms MUST independently express enablement, registered-agent limits,
  concurrent-execution limits, capability entitlements, and one or more usage meters.
- **FR-012**: Usage meters MUST identify a commercial metric, accounting period, and limit; the
  initial required combination is AI_EXECUTION_UNIT with a MONTHLY period.

#### Offline Activation and Renewal

- **FR-013**: A newly initialized License Server MUST be UNACTIVATED and MUST create a stable unique
  server identifier, an asymmetric installation identity, creation time, and installation metadata.
- **FR-014**: The installation private identity MUST remain local and MUST never appear in exported
  requests, logs, status results, or audit records.
- **FR-015**: An activation request MUST identify licenseServerId, installation public key and
  fingerprint, licenseId, customerId, a unique nonce, creation time, and optional environment
  evidence.
- **FR-016**: Vendor administrators MUST be able to inspect an activation request before issuing a
  certificate.
- **FR-017**: An activation certificate MUST bind commercial license ID, customer ID, server ID,
  server public-key fingerprint, activation identifier, permitted activation policy, and issue time.
- **FR-018**: Activation import MUST verify both signatures, schema support, license and customer
  correspondence, local server identity, activation policy, and applicable validity.
- **FR-019**: Copying a license and activation certificate to an independently initialized server
  MUST fail without relying on vendor connectivity.
- **FR-020**: Offline renewal MUST preserve the server identity and MAY preserve activation only
  when the new license and activation policy authorize the same customer and server binding.
  Renewal MUST retain usage already recorded in the current accounting period and MUST NOT create a
  fresh quota period. If a renewal lowers a limit below current allocation, existing registrations
  and leases remain recorded, no new allocation is permitted until utilization is below the new
  limit, and active leases expire normally rather than being extended. Removed product, feature,
  or AI entitlements MUST be denied on the next authoritative check; the import result and status
  MUST report the resulting over-allocation warning.
- **FR-020A**: Routine License Server data backups MUST exclude the installation private key. A
  restore MAY retain activation only on the original installation when its existing local private
  identity matches the restored server identity and activation certificate. Backup MUST use one
  coordinated SQLite snapshot, verify SQLite integrity before reporting success, refuse to
  overwrite an existing destination, remove an incomplete destination after failure, and leave the
  authoritative database unchanged on failure.
- **FR-020B**: Migration to new hardware, loss of the local installation private key, or deliberate
  identity reset MUST create a new License Server identity and require offline reactivation.
- **FR-020C**: The POC MUST reject copying only license and activation artifacts to another server,
  but advanced detection of a complete VM clone is deferred and MUST NOT be claimed as provided.

#### Product Instances, Environments, and DR

- **FR-021**: A product installation MUST register a stable logical instanceId, installation public
  identity, product identity, deployment type, environment type, and application version.
- **FR-022**: Normal product setup MUST automatically generate and persist one installation key pair
  and stable instanceId, register its public identity with the configured License Server, and avoid
  requiring customers to manage those identity artifacts manually during ordinary installation.
- **FR-022A**: For the POC, the License Server MUST authenticate protected instance operations using
  signed requests over internal HTTPS that prove possession of the registered installation private
  key; the contract MUST permit future mTLS without replacing the logical instance identity.
- **FR-023**: Instance identity MUST NOT depend solely on host, network, pod, container, or process
  identifiers.
- **FR-023A**: One logical Kubernetes installation MUST consist of one product deployment whose
  replicas all use the same installation identity and represent the same product and environment.
- **FR-023B**: Installation identity MUST live outside individual pod lifecycles and be included in
  the customer's protected installation-state backup process.
- **FR-024**: Environment admission MUST atomically enforce the active license's per-type limits and
  return ENVIRONMENT_NOT_ENTITLED or ENVIRONMENT_LIMIT_EXCEEDED as applicable.
- **FR-025**: All pods of one Kubernetes deployment MUST reuse one logical instance identity, so
  scaling, rollout, restart, and rescheduling do not consume additional environment capacity.
- **FR-026**: A Tomcat installation MUST retain its logical identity across ordinary restarts and
  MUST NOT consume a new environment entitlement.
- **FR-026A**: Recreating a Kubernetes namespace with its restored installation identity MUST resume
  the existing registration. Recreating it without that identity MUST generate a new identity and
  MUST NOT claim, overwrite, or reuse the prior registration implicitly.
- **FR-027**: Registration state MUST be distinct from active production-class consumption state.
- **FR-027A**: Product instance lifecycle states MUST be REGISTERED, STANDBY, ACTIVE, and
  DECOMMISSIONED, with every transition authorized, validated, and audited.
- **FR-027B**: Normal decommission MUST wait for active human and agent leases to drain. An explicit
  audited force-decommission operation MUST revoke those leases and all cached entitlement
  certificates bound to the instance in central state before completing.
- **FR-027D**: Force decommission MUST warn that a disconnected client cannot receive immediate
  revocation and MAY continue only until its already-issued cached certificate expires. No new or
  renewed cached certificate may be issued after central revocation.
- **FR-027C**: A DECOMMISSIONED instance MUST stop consuming its environment entitlement, and that
  capacity MUST become immediately available for another eligible registration. Its old identity
  MUST NOT silently reactivate or reclaim the allocation.
- **FR-028**: A licensed DR instance MUST support STANDBY and ACTIVE states without Internet access;
  STANDBY consumes its DR registration allowance but not active production-class capacity.
- **FR-029**: DR promotion MUST atomically place the selected DR instance in ACTIVE and the prior
  PROD instance in a non-active state while enforcing the active production-class limit, including
  when requests overlap or the prior PROD application is unavailable.
- **FR-029A**: Failback MUST use the same atomic handoff in reverse, placing PROD in ACTIVE and DR in
  STANDBY without any interval in authoritative state where both are active.

#### Human Entitlements

- **FR-030**: Named-user assignment MUST be explicit and customer-wide by default; account-table
  presence alone MUST NOT consume a named license.
- **FR-030A**: Released named-user capacity MUST have a 30-day reassignment cooldown. An
  administrator MAY bypass the cooldown only for employee departure or assignment correction by
  recording a reason, actor, timestamp, previous user, and replacement user in audit history.
- **FR-031**: Licensed humans MUST be distinguishable from service and system accounts.
- **FR-032**: Category limits, initially REPORTING and DQ_ISSUE_MANAGER, MUST be enforceable
  independently of the global named-user limit.
- **FR-033**: Concurrent-user leases MUST identify leaseId, userId, product instance, sessionId,
  acquiredAt, heartbeatAt, and expiresAt; the POC default lease duration MUST be five minutes with
  a heartbeat every one minute.
- **FR-034**: Concurrent capacity checking and lease creation MUST be atomic across all registered
  environments in the pool; exactly one request can claim the last slot.
- **FR-035**: Leases MUST support heartbeat, explicit release, and automatic removal from counted
  capacity after expiry without manual intervention.
- **FR-035A**: Browser, session, or application failure that stops heartbeats MUST return concurrent
  capacity no later than five minutes after the last successful heartbeat.
- **FR-035B**: License Server restart MUST preserve unexpired leases and their absolute expiry
  times; downtime MUST NOT extend a lease.

#### Product and Feature Entitlements

- **FR-036**: Applications MUST be able to request stable allow-or-deny decisions for products,
  features, and product user capacity without reading the commercial license representation.
- **FR-037**: Each decision MUST include a stable machine-readable reason code and sufficient
  non-secret context for diagnosis.
- **FR-038**: Product and feature decisions MUST apply only to registered, authenticated instances
  and the currently valid customer-wide entitlement state.

#### AI Entitlements and Metering

- **FR-039**: AI enablement and capability decisions MUST be independent of all human-seat
  availability unless the commercial license explicitly links them.
- **FR-040**: Agent registration MUST be customer-wide and MUST atomically enforce the maximum
  registered-agent count.
- **FR-041**: Agent execution leases MUST identify leaseId, agentId, executionId, product instance,
  acquiredAt, heartbeatAt, and expiresAt; the POC default lease duration MUST be five minutes with
  a heartbeat every one minute.
- **FR-042**: Agent execution capacity checking and lease creation MUST be atomic, with heartbeat,
  explicit release, and expiry recovery.
- **FR-043**: AI usage MUST aggregate across all customer environments and installations sharing
  the meter pool.
- **FR-044**: Usage consumption MUST atomically compare requested units with the remaining allowance
  and MUST never record use above the limit.
- **FR-045**: Meter periods MUST be calculated consistently in the customer estate, and a period
  transition MUST neither lose nor duplicate usage.
- **FR-045A**: Unused AEU MUST expire at the end of each monthly period and MUST NOT roll into a later
  period. License renewal or replacement during a period MUST retain the usage already consumed in
  that period for the same customer and meter.
- **FR-046**: AEU weighting MUST be governed by centrally defined product policy, and the entitlement
  model MUST permit later metrics such as tokens, executions, workflows, tool calls, records, and
  compute minutes.

#### Offline Continuity and Failure Policy

- **FR-047**: Vendor connectivity MUST NOT be required for activation validation, entitlement
  decisions, expiration, metering, administration, or audit during normal customer operation.
- **FR-048**: The License Server MUST issue finite cached entitlement certificates signed by its
  activated installation identity and bound to a registered product instance.
- **FR-049**: A cached certificate MUST identify certificateId, licenseId, instanceId, issuedAt,
  expiresAt, products, features, and its offline-operation policy. Its POC validity MUST be at most
  24 hours and MUST NOT extend beyond commercial license or grace expiry.
- **FR-050**: Product applications MUST be able to verify cached certificates locally using public
  verification material and reject modified, expired, or incorrectly bound certificates.
- **FR-051**: Valid cached certificates MUST permit listed existing product operations and feature
  checks during temporary License Server unavailability. They MUST NOT authorize a new licensed
  session or other new allocation.
- **FR-052**: New named-user assignment, environment registration, and agent registration MUST be
  denied while the central License Server is unavailable.
- **FR-053**: New autonomous execution MUST be denied during an outage unless an explicit local
  execution lease remains valid; centrally metered usage MUST stop after any licensed bounded
  offline allowance is exhausted.
- **FR-053A**: The POC default bounded offline AEU allowance MUST be zero; disconnected usage that
  requires central metering MUST be denied rather than buffered.
- **FR-054**: Concurrent human login during an outage MUST default to denial; any alternative policy
  MUST define and enforce a bounded capacity that cannot silently multiply the central pool.
- **FR-054A**: An existing session using cached permission MUST resume lease heartbeat or reacquire
  a lease when central connectivity returns and MUST stop licensed operation if capacity is denied.

#### Operations, Audit, Persistence, and Time

- **FR-055**: Administrators MUST be able to view customer and license identity, subscription and
  activation status, environment utilization, named and concurrent users, products and features,
  registered and active agents, AI meter usage, grace state, and warnings.
- **FR-056**: Administrative operations MUST cover activation request and import, instance list and
  lifecycle, named assignments, human leases, agent registration and leases, usage consumption,
  cached entitlement issuance, entitlement and utilization views, and audit retrieval.
- **FR-057**: Vendor-only operator commands MUST provide key generation, license issue, inspection
  and verification, activation-request inspection, activation-certificate issue, and
  activation-certificate inspection.
- **FR-058**: Customer-side commands or equivalent administrative operations MUST provide license
  status, activation-request generation, license and activation import, instance listing, and usage
  listing.
- **FR-059**: Administrative results MUST use stable machine-readable outcomes and the constitution's
  minimum reason-code catalog.
- **FR-060**: Meaningful decisions MUST create audit records identifying timestamp, customer,
  license, product instance when applicable, operation, entitlement, result, reason code, and
  relevant usage values.
- **FR-061**: Audit and diagnostic output MUST NOT reveal vendor or installation private keys,
  cryptographic seeds, authentication secrets, or equivalent sensitive material.
- **FR-062**: Server identity, license and activation artifacts, registrations, environment state,
  assignments, leases, agent state, metering, trusted time, and audit history MUST survive restart.
- **FR-063**: Integrity failure in persistent licensing state MUST produce STATE_INTEGRITY_FAILURE
  and MUST NOT silently grant capabilities.
- **FR-063A**: The License Server MUST compare restored database state with a separately retained
  local trusted checkpoint. If the database is older, the server MUST enter RECOVERY_REQUIRED,
  deny new allocations and usage consumption, and require an audited administrator reconciliation.
- **FR-063B**: Complete deletion of identity and authoritative persistent state MUST result in a new
  installation identity and UNACTIVATED status on initialization; prior activation MUST NOT be
  reconstructed from commercial license files alone.
- **FR-064**: Subscription evaluation MUST maintain last-known trusted local time and detect backward
  movement greater than the POC default tolerance of five minutes as CLOCK_ROLLBACK_DETECTED.
- **FR-064A**: CLOCK_ROLLBACK_DETECTED MUST deny new allocations, usage consumption, and issuance of
  cached entitlements until an audited administrator recovery restores a trusted time baseline.
- **FR-065**: Every entitlement operation MUST have an explicit, testable behavior for License
  Server availability, cache availability, grace state, and security-sensitive failure.
- **FR-066**: A runnable automated acceptance demonstration MUST prove all 40 scenarios listed in
  the feature request, including signing, activation, tamper and copy rejection, environment and DR
  enforcement, human and AI boundaries, outage behavior, restart persistence, expiry, and rollback.
- **FR-067**: Liveness MUST report UP whenever the process can serve diagnostics, including while
  UNACTIVATED. Readiness MUST report UP only after identity loading, schema migration, and storage
  integrity checks succeed; license expiry or UNACTIVATED state MUST be exposed as details rather
  than making the diagnostic endpoint unavailable. Missing/corrupt identity, failed migration,
  corrupt state, or RECOVERY_REQUIRED MUST make readiness DOWN with a stable non-secret reason.

### External Interface Commitments

The POC MUST expose a stable versioned administrative service rooted conceptually at
`/api/v1/license`, with these externally observable operations:

- `GET /status`, `GET /entitlements`, and `GET /utilization`.
- `POST /activation/request` and `POST /activation/import`.
- `POST /instances/register`, `GET /instances`, and `DELETE /instances/{id}`.
- `POST /instances/{id}/activate` and `POST /instances/{id}/standby`.
- `POST /users/named` and `DELETE /users/named/{userId}`.
- `POST /leases/users`, `POST /leases/users/{leaseId}/heartbeat`, and
  `DELETE /leases/users/{leaseId}`.
- `POST /agents` and `DELETE /agents/{agentId}`.
- `POST /leases/agents`, `POST /leases/agents/{leaseId}/heartbeat`, and
  `DELETE /leases/agents/{leaseId}`.
- `POST /usage`, `POST /cached-entitlements`, and `GET /audit`.

Vendor-only command names MUST include `keygen`, `issue-license`, `inspect-license`,
`verify-license`, `inspect-activation-request`, `activate-license-server`, and
`inspect-activation-certificate`. Customer-side command names or equivalent administrative
operations MUST include `license-status`, `generate-activation-request`, `import-license`,
`import-activation`, `list-instances`, and `list-usage`.

These names, inputs, outcomes, reason codes, and compatibility behavior are external product
contracts. Technology stack, transport implementation, and command packaging remain planning
decisions.

### Required Reason Codes

The POC MUST support at least LICENSE_VALID, LICENSE_SIGNATURE_INVALID, LICENSE_FORMAT_INVALID,
LICENSE_SCHEMA_UNSUPPORTED, LICENSE_NOT_YET_VALID, LICENSE_EXPIRED, LICENSE_GRACE_PERIOD,
LICENSE_SERVER_NOT_ACTIVATED, LICENSE_SERVER_IDENTITY_MISMATCH, ENVIRONMENT_LIMIT_EXCEEDED,
ENVIRONMENT_NOT_ENTITLED, INSTANCE_NOT_REGISTERED, INSTANCE_AUTHENTICATION_FAILED,
ACTIVE_PRODUCTION_LIMIT_EXCEEDED, NAMED_USER_LIMIT_EXCEEDED, CONCURRENT_USER_LIMIT_EXCEEDED,
USER_LEASE_INVALID, PRODUCT_NOT_ENTITLED, FEATURE_NOT_ENTITLED, PRODUCT_USER_LIMIT_EXCEEDED,
AI_NOT_ENTITLED, AGENT_REGISTRATION_LIMIT_EXCEEDED, AGENT_CONCURRENCY_LIMIT_EXCEEDED,
AI_USAGE_LIMIT_EXCEEDED, CLOCK_ROLLBACK_DETECTED, LICENSE_SERVER_UNAVAILABLE,
CACHED_ENTITLEMENT_EXPIRED, CACHED_OPERATION_NOT_ALLOWED, and STATE_INTEGRITY_FAILURE.
The clarified POC policies MUST additionally support NAMED_USER_REASSIGNMENT_COOLDOWN and
STATE_ROLLBACK_DETECTED.

### Key Entities *(include if feature involves data)*

- **Commercial License**: Versioned, vendor-signed customer terms containing identity, subscription,
  activation policy, environments, human users, products, features, AI, and meters.
- **Customer**: The licensed enterprise identity and administrative contact associated with all
  customer-wide pools.
- **Subscription**: License type, tier, validity interval, grace policy, and trusted-time status.
- **License Server Identity**: Stable server identifier, installation public identity and
  fingerprint, creation time, metadata, and protected local private identity.
- **Activation Request**: Offline request binding a customer license reference and nonce to a
  License Server's public identity.
- **Activation Certificate**: Vendor-signed approval binding a commercial license and activation
  policy to one License Server identity.
- **Product Instance**: Stable logical Tomcat or Kubernetes installation with product, deployment,
  environment, application version, public identity, and lifecycle state.
- **Environment Allocation**: Consumption of a licensed environment-type capacity by a logical
  instance, separate from active production-class status, with REGISTERED, STANDBY, ACTIVE, and
  DECOMMISSIONED lifecycle states.
- **Named User Assignment**: Explicit relationship between a licensed human, applicable pool,
  category, and product scope.
- **User Lease**: Expiring concurrent-session allocation tied to user, session, and product instance.
- **Product Entitlement**: Enabled product, product user capacity, and associated feature set.
- **Agent Registration**: Customer-wide licensed AI agent identity and enabled capabilities.
- **Agent Execution Lease**: Expiring autonomous-execution allocation tied to agent, execution, and
  product instance.
- **Usage Meter and Period**: Commercial metric, accounting period, limit, consumed amount, and
  period boundary shared across the customer estate.
- **Cached Entitlement Certificate**: Finite, instance-bound, server-signed product and feature
  permissions with explicit outage policy.
- **Audit Event**: Non-secret evidence of a licensing operation, decision, reason, identities, and
  relevant utilization.
- **Trusted-Time State**: Last accepted local time and rollback evaluation needed for offline
  subscription enforcement.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The automated acceptance demonstration completes all 40 required scenarios with 100%
  of expected allow and deny outcomes and stable reason codes.
- **SC-002**: In every tamper test, any protected one-field change to an issued license or activation
  certificate is rejected before an entitlement is granted.
- **SC-003**: In 100% of cross-server copy tests, independently initialized servers reject artifacts
  activated for another server identity.
- **SC-004**: Across Kubernetes scaling from 3 to 20 replicas, repeated pod replacement, and Tomcat
  restart, reported logical environment consumption remains exactly one per installation.
- **SC-005**: Across at least 1,000 contested last-slot trials for human and agent leases, successful
  allocations never exceed their configured limit and exactly one eligible request wins the final
  slot.
- **SC-006**: Across at least 1,000 contested usage-boundary trials, recorded AEU never exceeds the
  monthly limit and no successful consumption is lost.
- **SC-007**: After a controlled License Server restart, 100% of identity, activation,
  registrations, assignments, environment state, meter usage, and still-valid leases match their
  pre-restart authoritative values.
- **SC-008**: With all vendor and external network paths unavailable, 100% of normal activation
  validation, entitlement enforcement, expiry, metering, status, and audit scenarios remain
  operable inside the customer estate.
- **SC-009**: During a simulated License Server outage, every operation follows its documented
  policy: allowed cached checks continue until expiry, and all prohibited capacity-increasing
  operations are denied.
- **SC-010**: Administrators can generate an activation request, import valid artifacts, determine
  current utilization, and identify the reason for any demonstrated denial without access to
  vendor support or hidden system data.
- **SC-011**: Every meaningful decision in the acceptance demonstration has one audit record with
  the expected reason and context, and zero audited or displayed records contain private keys,
  seeds, or authentication secrets.
- **SC-012**: Expiry, end-of-grace, and significant backward-clock tests each produce the required
  deterministic result without vendor connectivity in 100% of runs.
- **SC-013**: Tests of named-user cooldown, monthly AEU rollover, mid-period renewal, seven-day
  restricted grace, five-minute clock rollback, stale database restoration, and complete state
  deletion each produce the specified result in 100% of runs.

## Assumptions

- The POC represents one customer estate and one authoritative License Server instance; full high
  availability is deferred, but no requirement assumes sticky sessions or in-process-only state.
- Customer administrators can securely transport license and activation files between the vendor
  and air-gapped estate using their approved removable-media process.
- Customer infrastructure administrators may control hosts, virtual machines, files, and clocks.
  The POC prevents normal copying and casual cloning but does not claim perfect resistance to a
  privileged attacker without hardware-backed identity.
- Routine data backup and restore occurs on the original License Server installation while its
  separately protected local installation identity remains available. Hardware migration is an
  offline reactivation workflow, not a transparent backup restore.
- The customer provides stable persistent storage and protects installation private material using
  ordinary enterprise host controls. Hardware-backed TPM or HSM identity remains a future option.
- Product installations create and persist one logical identity during installation. Kubernetes
  replicas share that identity through customer-managed deployment state; Tomcat retains it across
  restarts.
- Product identity generation and registration are automatic parts of ordinary installation. The
  customer supplies the License Server location but does not manually generate product keys.
- A customer-wide pool is the default for every entitlement. Separate pools exist only when an
  issued commercial license explicitly identifies them.
- The POC uses five-minute leases, one-minute heartbeats, cached certificates valid for at most 24
  hours, and no disconnected AEU consumption. Future licenses may configure other finite values
  without changing the POC's documented default behavior.
- Normal renewal for the same customer and server can reuse activation only when explicit renewal
  policy authorizes it; otherwise a new offline activation certificate is required.
- The initial POC supports one monthly calendar convention consistently within the customer estate;
  its precise timezone boundary is a planning decision recorded with the meter policy, and unused
  AEU does not roll over.
- The POC defaults to a seven-day restricted subscription grace period and five-minute clock
  rollback tolerance. A future schema revision may make these and the named-user cooldown
  commercially configurable while preserving explicit, testable policy.
- Future high availability will preserve one logical License Server authority with coordinated
  transactional writes and no sticky-session dependency. Replica topology, leader election,
  quorum, and failover objectives are deliberately deferred to a later HA feature.

## Out of Scope

- Vendor SaaS portal, online phone-home activation, billing, payment, and Internet telemetry.
- Full enterprise License Server high availability.
- TPM, HSM, or other hardware-rooted identity implementation.
- Advanced prevention or reliable detection of a customer copying and running a complete License
  Server VM; this may be hardened after the POC.
- Customer single sign-on and production public-key-infrastructure automation.
- Claims of perfect protection from cloning, rollback, binary modification, or database tampering by
  a customer administrator with complete infrastructure control.
