# Local Quickstart and Validation Guide

This guide defines the runnable target for the completed POC. Commands assume execution from
`offline-license-platform/` after implementation. The License Server and sample product run as
separate local Java processes; Docker, Testcontainers, Docker Compose, Helm, and PostgreSQL are not
required.

## Prerequisites

- Java 21
- Maven Wrapper included by the project
- Bash-compatible shell for the reference scripts
- `jq` for acceptance assertions
- A writable local runtime directory
- No Internet access after Maven dependencies have been staged

The License Server owns its SQLite file and identity directory. It never accesses a product
application's database.

## Stage for an Air-Gapped Environment

On a connected build workstation:

```bash
./mvnw -DskipTests dependency:go-offline
./mvnw clean verify
tar -czf offline-license-platform-bundle.tgz \
  pom.xml .mvn mvnw mvnw.cmd \
  license-*/target examples/*/target deployment scripts samples docs
```

Transfer the application bundle and a populated, checksum-verified Maven repository using the
customer's approved media process. Vendor private keys stay only on the vendor signing workstation.

In the disconnected customer environment:

```bash
./mvnw -o verify
```

Expected: the complete reactor and tests succeed with external networking disabled.

## Run Validation Suites

```bash
./mvnw -o test
./mvnw -o verify -Pconcurrency-tests
./mvnw -o verify -Psecurity-tests
./mvnw -o verify -Poutage-tests
```

Expected:

- Domain and crypto tests reject malformed, modified, unsupported, expired, and mismatched data.
- Temporary-file SQLite tests run 1,000 contested final-slot cases for environments, named users,
  user leases, agent registrations, agent leases, and AEU without oversubscription.
- Local-process tests launch the server JAR on an ephemeral HTTPS port and exercise the real client.
- Restart tests preserve absolute lease expiry and authoritative state.
- Restore tests detect a database sequence older than the separate trusted checkpoint.
- Outage tests permit only valid cached product/feature checks and deny central-only operations.

## Initialize the Local Runtime

Generate fresh development TLS and administrator material. The helper must fail if asked to reuse a
default or repository-provided secret.

```bash
./scripts/setup-local-runtime.sh
```

Expected local state:

```text
.local/
├── admin-token
├── ca.pem
├── license-server/
│   ├── data/license-state.sqlite
│   ├── identity/
│   │   ├── identity.pk8
│   │   ├── identity.spki
│   │   └── trusted-checkpoint.json
│   └── logs/
└── sample-product/
    ├── identity/
    ├── cache/
    └── logs/
```

The setup command creates no vendor key and no activation.

## Start Locally

In one terminal:

```bash
./scripts/run-license-server-local.sh
```

In a second terminal after activation, provide the local server coordinates and durable product
identity path. The activation request supplies the server ID and public key used to verify cached
entitlements; the product private key remains only in the identity file.

```bash
export LICENSE_SERVER_URL=http://127.0.0.1:8443
export LICENSE_SERVER_ADMIN_TOKEN="$(cat .local/admin-token)"
export LICENSE_SERVER_ACTIVATION_REQUEST_FILE="$PWD/activation-request.json"
export SAMPLE_PRODUCT_IDENTITY_FILE="$PWD/.local/sample-product/identity/instance.json"
./scripts/run-sample-product-local.sh
```

The server runs as an executable Java 21 JAR. The sample runner builds and starts the real example,
checks ACTIVE status, registers its durable identity idempotently, checks product and feature
entitlements, acquires a human lease, acquires an autonomous-agent execution lease, meters an AI
request, and releases both leases. It exits nonzero at the first denied or invalid result. PID state
is recorded under `.local/pids` while the flow runs and is removed on success, failure, or signal;
stopping a process does not delete SQLite, identity, cache, or audit state.

Verify initial state:

```bash
./scripts/customer-cli.sh license-status --output json
```

Expected: `allowed=false`, reason `LICENSE_SERVER_NOT_ACTIVATED`, and state `UNACTIVATED`.

## Issue and Activate Offline

Generate vendor material only in a vendor-side working directory:

```bash
java -jar license-issuer-cli/target/license-issuer-cli.jar keygen \
  --private-key ./vendor-private.pk8 \
  --public-key ./vendor-public.spki

java -jar license-issuer-cli/target/license-issuer-cli.jar issue-license \
  --input samples/licenses/a-inc-entitlements.json \
  --private-key ./vendor-private.pk8 \
  --output ./a-inc-license.json
```

Generate the customer request, sign it on the vendor workstation, and import the returned artifacts:

```bash
./scripts/customer-cli.sh generate-activation-request \
  --license-id LIC-AINC-2026-001 \
  --customer-id AINC \
  --output ./activation-request.json

java -jar license-issuer-cli/target/license-issuer-cli.jar inspect-activation-request \
  --input ./activation-request.json

java -jar license-issuer-cli/target/license-issuer-cli.jar activate-license-server \
  --request ./activation-request.json \
  --license ./a-inc-license.json \
  --private-key ./vendor-private.pk8 \
  --output ./activation-certificate.json

./scripts/customer-cli.sh import-activation \
  --license ./a-inc-license.json \
  --activation ./activation-certificate.json
```

Expected: status becomes ACTIVE. The vendor private key is absent from customer runtime directories,
process arguments, distributions, application resources, and logs.

## Run the Ordered POC Demonstration

```bash
./scripts/poc-demo-local.sh
```

The script starts and stops local Java processes itself, uses isolated temporary runtime
directories, and executes all 40 ordered acceptance checks. Its report must show 40 passed and 0
failed, including:

- modified license and wrong-server activation rejection;
- Kubernetes logical identity simulated across 3 then 20 client replicas;
- environment exhaustion and atomic PROD/DR handoff;
- global named-user and concurrent-user limits with lease expiry;
- agent registration, execution, and monthly AEU boundaries;
- cached product/feature success while the License Server process is stopped;
- denial of every central-only operation during the outage;
- restart persistence, stale backup detection, expiration, grace, and clock rollback.

## Stop Without Deleting State

```bash
./scripts/stop-local-processes.sh
./scripts/customer-cli.sh license-status --output json
```

The final status command should report `LICENSE_SERVER_UNAVAILABLE`. Restarting the server must load
the same installation identity and authoritative state.

## Inspect Status and Audit

```bash
./scripts/customer-cli.sh license-status --output json
./scripts/customer-cli.sh list-instances --output json
./scripts/customer-cli.sh list-usage --output json
curl --fail --cacert .local/ca.pem \
  -H "Authorization: Bearer $(<.local/admin-token)" \
  'https://localhost:8443/api/v1/license/audit?limit=100'
```

Expected: all results use the common decision envelope. Audit includes denials and lifecycle events
but contains no private keys, seeds, bearer tokens, or successful-heartbeat noise.

## Validate Backup and Restore

```bash
./scripts/backup-license-state.sh --output ./license-state-backup.sqlite
./scripts/restore-license-state.sh --input ./license-state-backup.sqlite
```

Expected: backup uses a consistent SQLite snapshot and excludes the License Server private key.
Restore succeeds only on the original installation with its separate matching identity. An older
sequence enters RECOVERY_REQUIRED. Moving the database backup to new hardware creates a new identity
and requires offline reactivation.

## Kubernetes and Tomcat Design Examples

The example modules compile and run against the local License Server:

```bash
./mvnw -o -pl examples/tomcat-example verify
./mvnw -o -pl examples/kubernetes-example verify
```

The Kubernetes integration test launches multiple application contexts using one mounted test
identity to prove that all replicas represent one logical installation. Documentation defines the
future Secret/bootstrap Job, namespace backup, and re-creation behavior, but no cluster, container,
or Helm installation is required in this POC.

## Contract References

- HTTP surface: `contracts/openapi.yaml`
- Signed envelope: `contracts/signed-envelope.schema.json`
- Commercial license: `contracts/commercial-license.schema.json`
- Activation request/certificate: `contracts/activation-request.schema.json` and
  `contracts/activation-certificate.schema.json`
- Cached entitlement: `contracts/cached-entitlement.schema.json`
- CLI behavior: `contracts/cli-contracts.md`

## Final Exit Criteria

The POC is ready only when all offline Maven verification profiles pass, the local 40-step script
reports no failures, License Server stop/restart preserves state, external networking is unnecessary,
and no vendor private key appears in any customer runtime artifact or local process.
