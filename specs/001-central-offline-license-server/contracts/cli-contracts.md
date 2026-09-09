# CLI Contracts

## Common Rules

- Commands emit human-readable text by default and stable JSON with `--output json`.
- Success exits `0`; licensing denial exits `2`; invalid input/artifact exits `3`; local I/O exits
  `4`; unavailable License Server exits `5`; unexpected internal failure exits `10`.
- JSON output uses the same decision/reason envelope as the HTTP API where an entitlement decision
  occurs.
- Secrets are accepted only through explicit file paths or protected console input, never command
  arguments, default resources, samples, or logs.
- Artifact writes default to create-new semantics and require `--force` to replace a named output.

## Vendor-Only Commands

### `keygen`

Inputs: explicit private-key output, public-key output, and optional key ID. Produces Ed25519 PKCS#8
private and X.509 public key files with restrictive private-file permissions. No default key exists.

### `issue-license`

Inputs: entitlement JSON, explicit vendor private-key file, key ID, and output path. Validates the
typed schema, canonicalizes payload bytes, signs the commercial-license envelope, and writes it.

### `inspect-license`

Inputs: license envelope. Displays envelope metadata and parsed non-secret payload without claiming
signature validity.

### `verify-license`

Inputs: license envelope and explicit vendor public-key file. Verifies envelope, signature, schema,
and basic validity and returns a stable reason.

### `inspect-activation-request`

Inputs: activation-request JSON. Displays server/customer/license binding, fingerprint, nonce, and
creation time and validates its shape and proof-of-possession signature.

### `activate-license-server`

Inputs: activation request, matching commercial license, explicit vendor private-key file,
activation policy, and output path. Produces a vendor-signed activation-certificate envelope.

### `inspect-activation-certificate`

Inputs: activation-certificate envelope and optional vendor public key. Displays binding fields and,
when a key is supplied, verifies the signature.

## Customer Commands

Customer commands accept `--server`, customer CA/trust file, and protected administrator-token file
as applicable. No command requires Internet access.

- `license-status`: returns activation, validity, grace, warnings, and summarized utilization.
- `generate-activation-request`: supplies license/customer IDs and writes the offline request.
- `import-license`: stages and verifies a commercial license without activating it alone.
- `import-activation`: atomically imports matching license and activation envelopes.
- `list-instances`: lists identity, product, environment, lifecycle, and allocation state.
- `list-usage`: lists meter periods, limits, consumption, and remaining amounts.
- `failover FILE`: atomically hands active production service to the registered DR standby described by the JSON command file.
- `failback FILE`: atomically hands active DR service back to the registered production standby described by the JSON command file.

## Compatibility

Adding optional JSON response fields is backward compatible. Removing/renaming fields, changing
exit-code meaning, or changing signed artifact bytes requires an explicit contract version. Unknown
reason codes are preserved in raw output and mapped to a generic local enum value by older clients.
