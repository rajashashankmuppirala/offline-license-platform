# Local POC Demonstration Guide

This guide explains how to demonstrate the offline License Server POC on one workstation and what
each part proves. It offers two complementary paths:

1. **Automated acceptance demo** — the recommended repeatable proof of all 40 acceptance outcomes.
2. **Hands-on demo** — starts a persistent server, performs offline issuance and activation,
   onboards the sample product, exercises licensing, and inspects durable state.

The automated demo is the strongest acceptance evidence because it asserts exact results and stops
at the first failure. The hands-on demo is better for explaining the architecture to an audience.

## What the POC achieves

| Capability | Demonstrated behavior | Value |
|---|---|---|
| Offline licensing | A vendor Ed25519 key signs a versioned commercial license; runtime needs only the public key | Entitlements work without a vendor or Internet connection |
| Bound activation | The server generates an identity and proof-bearing request; activation binds its UUID and key fingerprint | Artifacts copied to an independently initialized server are rejected |
| Environment control | PROD, DR, UAT, QA, DEV, and OTHER allocations are centrally and atomically counted | One customer-wide policy governs multiple installations |
| PROD/DR lifecycle | REGISTERED, STANDBY, ACTIVE, and DECOMMISSIONED states with atomic failover/failback | Passive DR does not permit two active production authorities |
| Kubernetes identity | Scaling from 3 to 20 replicas and normal rollout retains one logical registration | Pods are not incorrectly counted as product installations |
| Human licensing | Named seats, category/product limits, cooldown, and expiring concurrent-user leases | Named and concurrent models remain distinct and centrally governed |
| Product features | Typed product, feature, and per-product user-capacity decisions | Product code does not parse commercial-license JSON |
| AI licensing | Registered-agent limits, concurrent execution leases, action weighting, and monthly AEU quota | Human seats and AI capacity remain independently controlled |
| Outage handling | A signed, instance-bound cache permits bounded product/feature checks; allocation and metering fail closed | Temporary server outage does not create unlimited capacity |
| Persistence | Activation, identity, instances, leases, assignments, meters, cache issuance, and audit survive restart | Restart cannot reset consumption |
| Security diagnostics | Signature, state-integrity, stale-restore, expiry, and clock-rollback checks return stable reason codes | Failures are explicit and supportable |
| Audit and recovery | Domain decisions carry exact reasons/context; coordinated backup validates integrity and identity | Operations can explain and safely preserve licensing state |

## Scope and boundaries

This is a Java 21, Spring Boot, embedded-SQLite POC. It requires no Docker, container runtime,
Kubernetes cluster, PostgreSQL server, or external runtime service.

It proves the behavior of the unmodified implementation under its documented trust model. It does
not prove resistance to a hostile administrator with root/hypervisor control. Complete VM cloning,
exportable software identities, altered binaries/configuration, shared logical identities,
suppressed metering calls, and bypassed product checks remain limitations. See
[`security-red-team-review.md`](security-red-team-review.md) for the full adversarial analysis.

## Prerequisites

Run all commands from `offline-license-platform/`.

Required:

- Java 21 or newer
- Maven 3.9 or newer
- POSIX-compatible shell
- `curl`
- `jq`

The optional backup walkthrough also requires the `sqlite3` CLI. In an air-gapped environment,
stage the Maven repository first; normal server/client/issuer operation makes no Internet call.

```bash
java -version
mvn -version
curl --version
jq --version
```

## Option A: automated 40-step acceptance demo

This is the recommended demo for technical acceptance. It uses isolated test state, exercises real
engine/storage/client behavior, launches real License Server processes for lifecycle and outage
scenarios, and exits nonzero at the first failed scenario group.

### Preview and run

```bash
./scripts/poc-demo-local.sh --list
./scripts/poc-demo-local.sh
echo "$?"
```

Previewing must show exactly 40 numbered steps. A real run prints a numbered step only after the
tests responsible for it pass. Success ends with steps 37–40 and exit status `0`:

```text
37. Restart the same License Server installation
38. Assert identity and authoritative state survived restart without lease extension
39. Advance beyond expiry and restricted grace and assert denial (LICENSE_EXPIRED) with exactly one decision audit
40. Move time backward beyond tolerance and assert denial (CLOCK_ROLLBACK_DETECTED) with exactly one decision audit
```

### What the eight scenario groups prove

#### Steps 1–12 — cryptography and activation

- Generate a vendor Ed25519 key pair and signed A Inc. license.
- Create a server identity, verify UNACTIVATED, and generate an activation request.
- Sign/import activation and reach ACTIVE.
- Reject a capacity change made without resigning as `LICENSE_SIGNATURE_INVALID`.
- Reject activation on a second independent server as `LICENSE_SERVER_IDENTITY_MISMATCH`.
- Assert one audit record per meaningful decision.

#### Steps 13–18 — environments and Kubernetes

- Register PROD and UAT logical instances.
- Reject a second PROD at capacity one with `ENVIRONMENT_INSTANCE_LIMIT_EXCEEDED`.
- Simulate 3-to-20 replicas, rollout, rescheduling, and namespace identity restoration.
- Prove replicas with one durable identity consume one environment allocation.

#### Steps 19–26 — users and product features

- Fill a customer-wide concurrent-user pool across environments.
- Reject the next lease as `CONCURRENT_USER_LIMIT_EXCEEDED`.
- Expire a lease, restart, and reuse capacity without extending the old lease.
- Fill named users and reject the next as `NAMED_USER_LIMIT_EXCEEDED`.
- Reject an absent feature as `FEATURE_NOT_ENTITLED`.

#### Steps 27–30 — agents

- Fill the registered-agent and execution-lease pools.
- Reject excess capacity with `AGENT_REGISTRATION_LIMIT_EXCEEDED` and
  `AGENT_CONCURRENCY_LIMIT_EXCEEDED`.

#### Steps 31–33 — AI usage

- Convert commercial actions to configured AEU weights.
- Atomically reach the exact monthly quota.
- Reject the next unit as `AI_USAGE_LIMIT_EXCEEDED`.
- Bind idempotency keys to their original instance, action, period, amount, pool, and dimensions.

#### Steps 34–36 — License Server outage

- Issue a signed cache bound to one server, instance key, product, features, and expiry.
- Stop the server and allow only the cached product/feature operations.
- Deny registrations, leases, and metering as `LICENSE_SERVER_UNAVAILABLE`.

#### Steps 37–38 — restart persistence

- Restart the same server as a separate Java process.
- Verify the same ID, ACTIVE state, exact utilization and lease expiries, cache row, product, and
  audit chain without vendor-network access.

#### Steps 39–40 — lifecycle and trusted time

- Advance beyond validity and grace and receive `LICENSE_EXPIRED`.
- Roll time backward beyond five minutes and receive `CLOCK_ROLLBACK_DETECTED`.

The script is executable evidence, not 40 unconditional `echo` statements. Each group must pass
before its steps print. It uses temporary fixtures/processes rather than leaving one persistent
40-step database; use Option B when an audience wants to inspect local state.

## Option B: hands-on local demo

Loopback HTTP is permitted only for this local workflow. A network or production deployment must
use customer-PKI TLS; the production profile refuses to start without TLS.

### 1. Prepare and build

Stop any previous demo processes:

```bash
./scripts/stop-local-processes.sh
```

To start fresh without deleting old data, move `.local` to a backup name if it exists:

```bash
mv .local ".local.before-demo-$(date +%Y%m%d-%H%M%S)"
```

Build and optionally run the runtime secret/dependency scan:

```bash
mvn clean package
./scripts/verify-runtime-artifacts.sh
```

Expected: `BUILD SUCCESS` and verification of seven packaged runtime archives. Key artifacts are:

```text
license-server/target/license-server-1.0.0-SNAPSHOT.jar
license-issuer-cli/target/license-issuer-cli.jar
examples/sample-product/target/sample-product-1.0.0-SNAPSHOT.jar
```

### 2. Issue the demonstration commercial license

```bash
./scripts/generate-a-inc-license.sh
```

Expected output includes `KEY_PAIR_GENERATED`, `LICENSE_ISSUED`, and:

```json
{"valid":true,"reasonCode":"LICENSE_VALID"}
```

The script creates:

```text
.local/vendor/vendor-private.key
.local/vendor/vendor-public.key
samples/licenses/generated/a-inc-license.signed.json
```

The private key represents vendor-only material. The A Inc. license grants one PROD, one passive
DR, two UAT, two QA, five DEV, 500 named users, 20 concurrent users, four products, three Validator
features, 10 agents, 3 concurrent AI executions, and 100,000 monthly AEU. It is valid through
2030-12-31 with seven days of restricted grace.

### 3. Start the customer License Server

```bash
export LICENSE_SERVER_URL=http://127.0.0.1:8443
export LICENSE_SERVER_ADMIN_TOKEN='replace-with-a-local-demo-secret'
export LICENSE_SERVER_VENDOR_PUBLIC_KEY_FILE="$PWD/.local/vendor/vendor-public.key"
./scripts/run-license-server-local.sh

until curl --silent --fail "$LICENSE_SERVER_URL/actuator/health/liveness" >/dev/null; do
  sleep 1
done
curl --silent "$LICENSE_SERVER_URL/actuator/health/liveness" | jq
```

Expected health status: `UP`. If startup fails, inspect:

```bash
tail -n 100 .local/license-server/server.log
```

Persistent state is placed under `.local/license-server`; the PID is under `.local/pids`.

### 4. Show first-start UNACTIVATED state

```bash
./scripts/customer-cli.sh license-status | jq '.data | {
  serverId, activationState, recoveryState, reasonCode
}'
```

Expected:

```json
{
  "serverId": "<new UUID>",
  "activationState": "UNACTIVATED",
  "recoveryState": "UNACTIVATED",
  "reasonCode": "LICENSE_SERVER_NOT_ACTIVATED"
}
```

Presenter point: first start creates a unique Ed25519 installation identity but grants no authority.

### 5. Generate and verify the offline activation request

```bash
mkdir -p .local/demo
printf '%s\n' '{
  "licenseId":"LIC-AINC-2026-001",
  "customerId":"AINC",
  "environmentEvidence":{"purpose":"local-poc-demo"}
}' > .local/demo/activation-request-input.json

./scripts/customer-cli.sh generate-activation-request \
  .local/demo/activation-request-input.json \
  | jq '.data' > .local/demo/activation-request.json

java -jar license-issuer-cli/target/license-issuer-cli.jar \
  inspect-activation-request \
  --request .local/demo/activation-request.json
```

Expected: `validProof` is `true`, and the request server ID matches status. The request includes the
server public identity, requested license/customer, nonce, timestamp, evidence, and proof signature;
it contains no private key.

### 6. Sign the activation on the vendor side

```bash
java -jar license-issuer-cli/target/license-issuer-cli.jar \
  activate-license-server \
  --request .local/demo/activation-request.json \
  --license samples/licenses/generated/a-inc-license.signed.json \
  --private-key .local/vendor/vendor-private.key \
  --public-key .local/vendor/vendor-public.key \
  --output .local/demo/activation-certificate.signed.json
```

Expected: `LICENSE_SERVER_ACTIVATED` and an activation ID. The CLI verifies the request proof and
commercial signature, checks license/customer correspondence, and signs the exact server UUID and
public-key fingerprint.

Optional inspection:

```bash
java -jar license-issuer-cli/target/license-issuer-cli.jar \
  inspect-activation-certificate \
  --certificate .local/demo/activation-certificate.signed.json
```

### 7. Import the signed license and activation

```bash
jq -n \
  --slurpfile license samples/licenses/generated/a-inc-license.signed.json \
  --slurpfile activation .local/demo/activation-certificate.signed.json \
  '{commercialLicense:$license[0],activationCertificate:$activation[0]}' \
  > .local/demo/activation-import.json

./scripts/customer-cli.sh import-activation .local/demo/activation-import.json | jq

./scripts/customer-cli.sh license-status | jq '.data | {
  activationState, recoveryState, reasonCode, licenseId, customer, subscription:.validity
}'
```

Expected: import is accepted with `LICENSE_VALID`; activation/recovery state become `ACTIVE`, and
license/customer are `LIC-AINC-2026-001`/`AINC`.

### 8. Show commercial-license tamper rejection

```bash
jq '.payload |= (fromjson | .users.maxConcurrentUsers = 200 | tojson)' \
  samples/licenses/generated/a-inc-license.signed.json \
  > .local/demo/a-inc-license.tampered.json

java -jar license-issuer-cli/target/license-issuer-cli.jar \
  verify-license \
  --license .local/demo/a-inc-license.tampered.json \
  --public-key .local/vendor/vendor-public.key
```

Expected: exit status `3` and `LICENSE_SIGNATURE_INVALID`. The original active license is unchanged.

### 9. Bootstrap and activate the sample product

```bash
export LICENSE_SERVER_ACTIVATION_REQUEST_FILE="$PWD/.local/demo/activation-request.json"
export SAMPLE_PRODUCT_IDENTITY_FILE="$PWD/.local/sample-product/identity/instance.json"
export SAMPLE_PRODUCT_CACHE_FILE="$PWD/.local/sample-product/cache/entitlement-cache.bin"
export SAMPLE_PRODUCT_USER='demo-user'
export SAMPLE_PRODUCT_SESSION='demo-session'
export SAMPLE_PRODUCT_AGENT='sample-agent'
export SAMPLE_PRODUCT_EXECUTION='demo-execution'
export SAMPLE_PRODUCT_REQUEST_ID='demo-ai-request-1'

./scripts/run-sample-product-local.sh
```

The first invocation is expected to exit nonzero after `STATUS ACTIVE`: it creates a durable product
key, registers the instance, and correctly finds it in `REGISTERED`, not `ACTIVE`, state. The runner
cleans its PID even on failure.

Find and activate that instance:

```bash
INSTANCE_ID=$(./scripts/customer-cli.sh list-instances \
  | jq -r '.data[] | select(.metadata.application == "sample-product") | .instanceId')
printf 'Sample instance: %s\n' "$INSTANCE_ID"

curl --fail-with-body --silent --show-error -X POST \
  -H "Authorization: Bearer $LICENSE_SERVER_ADMIN_TOKEN" \
  "$LICENSE_SERVER_URL/api/v1/license/admin/instances/$INSTANCE_ID/activate" | jq
```

Expected: accepted result, `LICENSE_VALID`, and instance state `ACTIVE`.

### 10. Register the demo agent and run the business flow

```bash
curl --fail-with-body --silent --show-error -X POST \
  -H "Authorization: Bearer $LICENSE_SERVER_ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  --data-binary "{\"agentId\":\"sample-agent\",\"displayName\":\"Local Demo Agent\",\"instanceId\":\"$INSTANCE_ID\"}" \
  "$LICENSE_SERVER_URL/api/v1/license/admin/agents" | jq

./scripts/run-sample-product-local.sh
```

Expected checkpoints:

```text
STATUS ACTIVE
INSTANCE ACTIVE <instance-id>
PRODUCT VALIDATOR LICENSE_VALID
FEATURE TABLEAU LICENSE_VALID
USER_LEASE ACTIVE <lease-id>
AI_EXECUTION_LEASE ACTIVE <lease-id>
AI_METER ACCEPTED 1/100000
SAMPLE_PRODUCT_FLOW COMPLETE
Sample product exited successfully; leases were released and PID state was cleaned up.
```

The product uses typed decisions, performs idempotent registration, checks Validator/TABLEAU and
product-user capacity, acquires human and agent leases, meters an interactive AI action, and releases
both leases through scoped cleanup.

### 11. Assign a named user

```bash
curl --fail-with-body --silent --show-error -X POST \
  -H "Authorization: Bearer $LICENSE_SERVER_ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  --data-binary '{
    "userId":"named-demo-user",
    "accountType":"HUMAN",
    "productId":"VALIDATOR",
    "category":"REPORTING"
  }' \
  "$LICENSE_SERVER_URL/api/v1/license/admin/users/named" | jq
```

Expected: accepted with `LICENSE_VALID`. Repeating it is idempotent and consumes no extra seat.

### 12. Inspect utilization and audit

```bash
./scripts/customer-cli.sh list-usage | jq '.data | {
  instances, namedUsers, activeConcurrentUsers,
  registeredAgents, activeAgentExecutions, meters
}'
```

After the sample exits, active lease arrays should be empty. The meter should contain one used AEU,
while the named assignment and registered agent remain.

```bash
./scripts/customer-cli.sh audit | jq '.data | reverse | .[0:15] | .[] | {
  occurredAt, operation, instanceId, entitlement, allowed, reasonCode, context
}'
```

Look for registration/activation, agent registration, product/feature/capacity checks, user and agent
lease decisions, usage consumption, and deliberate denials. Reason codes are stable API contracts,
not free-form log messages.

### 13. Prove restart persistence

```bash
./scripts/customer-cli.sh license-status | jq -r '.data.serverId' > .local/demo/server-id.before
./scripts/customer-cli.sh list-usage | jq '.data' > .local/demo/utilization.before.json

./scripts/stop-local-processes.sh
./scripts/run-license-server-local.sh
until curl --silent --fail "$LICENSE_SERVER_URL/actuator/health/liveness" >/dev/null; do
  sleep 1
done

test "$(cat .local/demo/server-id.before)" = \
  "$(./scripts/customer-cli.sh license-status | jq -r '.data.serverId')"
./scripts/customer-cli.sh license-status | jq '.data | {activationState,recoveryState,reasonCode}'
./scripts/customer-cli.sh list-usage | jq '.data'
```

Expected: the ID is unchanged, status is ACTIVE, assignments/agents/meter remain, and released leases
are not resurrected.

### 14. Optional coordinated backup

This step requires `sqlite3`:

```bash
./scripts/backup-license-server.sh "$PWD/.local/demo/license-server-backup.db"
./scripts/customer-cli.sh validate-restore \
  "$PWD/.local/demo/license-server-backup.db" | jq
```

Expected: backup creation refuses overwrite, SQLite integrity is `ok`, and validation is
`LICENSE_VALID` for the original identity. The database backup intentionally excludes the private
installation identity and separate trusted-time checkpoint. Use `restore-license-server.sh` only
with the server stopped; it stages the replacement and preserves the previous database.

### 15. Stop without deleting state

```bash
./scripts/stop-local-processes.sh
```

PID files are removed. Database, identities, artifacts, audit, logs, and demo evidence remain under
`.local` for inspection or another restart.

## Presenter run sheet

For a 15–20 minute presentation:

1. Show `samples/licenses/a-inc-license-input.json` and explain the commercial terms.
2. Preview and run the 40-step automated acceptance demo.
3. Show the persistent server's initial UUID and UNACTIVATED state.
4. Generate, inspect, sign, and import the offline activation exchange.
5. Show ACTIVE status and tampered-license rejection.
6. Bootstrap/activate the sample instance and register the agent.
7. Run the product/user/AI sample and inspect utilization/audit.
8. Restart and prove identity/state persistence.
9. Close with the limitations in `security-red-team-review.md`.

For a five-minute executive version, run only Option A, explain the eight groups, and present the
capability and boundary tables.

## Troubleshooting

- **Missing JAR:** run `mvn clean package`.
- **Port 8443 occupied:** run `./scripts/stop-local-processes.sh` or set matching
  `LICENSE_SERVER_PORT` and `LICENSE_SERVER_URL` values.
- **`UNAUTHORIZED`:** export the same non-empty admin token in the server and CLI terminals.
- **Server startup failure:** inspect `.local/license-server/server.log`; common causes are missing
  vendor public key, corrupt identity, database/checkpoint mismatch, or production without TLS.
- **First sample run fails:** expected for a new REGISTERED instance; activate it and register
  `sample-agent`, then rerun.
- **`LICENSE_SERVER_UNAVAILABLE`:** verify the process, loopback URL, activation-request/server
  identity match, and registered product identity.
- **Integrity/recovery failure:** preserve the database, identities, checkpoints, and logs. Inspect
  admin diagnostics/audit and do not delete checkpoints to silence the result.

## Demo success criteria

The local demonstration is complete when:

- all 40 automated steps print and exit status is `0`;
- the commercial license verifies as `LICENSE_VALID`;
- the server transitions from UNACTIVATED to ACTIVE through a signed offline exchange;
- tampering produces `LICENSE_SIGNATURE_INVALID`;
- the sample instance transitions from REGISTERED to ACTIVE;
- product/feature/user checks, human and agent leases, and AI metering succeed;
- both leases are released and visible utilization/audit match the actions;
- restart retains the same identity and durable state;
- optional backup passes integrity and original-identity validation;
- runtime needs no Docker, PostgreSQL, Internet, or vendor connection.
