# Offline License Platform PoC

A customer-hosted Java License Server with SQLite, offline Ed25519 license issuance/activation, signed product requests,
user and agent leases, AI metering, and signed entitlement caches.

Start with the **[local setup, run, and demo guide](docs/local-run-and-demo.md)**. It includes prerequisites, configuration,
both activation modes, exact commands, expected output, repeatable metering and restart demonstrations, troubleshooting,
and a 15–20 minute presentation outline.

```bash
# Java 21 target, Maven 3.9+; run from this directory
./scripts/build-local.sh
# Automated acceptance presentation (tests, not a persistent demo server)
./scripts/poc-demo-local.sh
```

For an interactive demo, follow the guide to issue a license, start the server, import the artifacts, and run the product
JVM. It needs no Docker, external database, or Kubernetes cluster. Stage Maven dependencies/plugins before offline use.

## What each script does

Run these scripts from this directory. Build first, then follow the guide to configure the server and product environment.

| Script | What it does | Creates keys, licenses, or certificates? |
| --- | --- | --- |
| `scripts/build-local.sh` | Runs `mvn clean verify`, including tests, and produces the server and issuer JARs | Tests use temporary fixtures; it does not issue your persistent demo license |
| `scripts/generate-a-inc-license.sh [KEY_DIR]` | Creates or reuses vendor Ed25519 keys, signs the sample license, and verifies its signature | Vendor private/public key files and `samples/licenses/generated/a-inc-license.signed.json`; no activation certificate |
| `scripts/run-license-server-local.sh` | Starts a background server using your environment configuration; tracks its PID and log | The server creates its installation identity and checkpoints on first startup; it does not issue a vendor license/certificate |
| `scripts/customer-cli.sh COMMAND` | Calls admin APIs for status, activation requests/imports, instances, usage, audits, handoffs, and backup validation | Can request public activation data or import existing signed artifacts; it does not vendor-sign them |
| `scripts/run-sample-product-local.sh` | Builds and runs the product JVM; registers/activates its instance, registers an agent, checks entitlements, uses leases and metering, then releases leases | Creates/reuses a product identity; does not issue a commercial license or activation certificate |
| `scripts/poc-demo-local.sh [--list]` | Runs eight acceptance-test groups and prints 40 milestones; `--list` only previews them | Test scenarios issue/sign temporary artifacts; no persistent interactive installation is left running |
| `scripts/verify-runtime-artifacts.sh` | Packages and scans seven runtime archives and their dependency graphs | No; verifies runtime isolation from vendor signing code and private-key/default-secret markers |
| `scripts/backup-license-server.sh TARGET` | Calls the online database backup API and checks the result with `sqlite3` | Database backup only; excludes private installation identity and external checkpoints |
| `scripts/restore-license-server.sh BACKUP` | Checks SQLite consistency and identity binding, then replaces the database while the server is stopped | No; startup still checks external signed checkpoints |
| `scripts/stop-local-processes.sh` | Stops the tracked server/product processes and waits for shutdown | No |

## Which key signs which artifact?

**The vendor private key signs the commercial license and activation certificate separately. The activation certificate
does not sign the license.** Neither artifact is a TLS/HTTPS certificate.

1. Build with `./scripts/build-local.sh`.
2. Run `./scripts/generate-a-inc-license.sh "$PWD/.local/demo-vendor"` to generate/reuse the vendor keys and issue the signed commercial license.
3. For activation-required mode, start the server and generate its public activation request.
4. Run the vendor issuer CLI's `activate-license-server` command to sign an activation certificate bound to that server identity.
5. Import the commercial license and activation certificate together. The server verifies both using the vendor public key.

The complete certificate-issuance command and import steps are in
[Demo B: activation-required mode](docs/local-run-and-demo.md#4-demo-b-activation-required-mode).
Validation-only mode skips vendor activation-certificate issuance and imports the signed license directly.
The scripts do not generate TLS certificates; the local walkthrough uses loopback HTTP.

- [Architecture and module responsibilities](docs/architecture-and-shipping.md)
- [PoC review and verification results](docs/poc-review.md)
- [Operational procedures](docs/operations.md)
- [Security boundaries and red-team assessment](docs/security-red-team-review.md)

Activation-required mode binds a license to an independently generated server identity. This software PoC does not
protect against a privileged administrator cloning the full installation or modifying the software.
