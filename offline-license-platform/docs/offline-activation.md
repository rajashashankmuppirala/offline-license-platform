# Offline Activation

Activation is required by default. Set `LICENSE_SERVER_ACTIVATION_REQUIRED=false` (equivalent Spring property:
`licensing.activation-required=false`) to run the POC in license-validation-only mode. In that mode, pass the signed
commercial-license envelope directly to `/api/v1/license/admin/licenses/import`, or run
`./scripts/customer-cli.sh import-license SIGNED_LICENSE_FILE`. The server still verifies the vendor signature, schema,
validity window, sequence, and revocation state, then stores the signed license as active. Status reports
`activationRequired: false` and `activationCertificatePresent: false`.

When `LICENSE_SERVER_ACTIVATION_REQUIRED=true`, the existing activation flow below is enforced. This is the secure default.

1. Start the server; it creates a stable local identity and reports UNACTIVATED.
2. Generate an activation request for the intended license and customer. It contains the server ID, public key,
   fingerprint, nonce, timestamp, and proof of private-key possession—never the private key.
3. Transfer the request to the vendor administrator, inspect it, and use the vendor CLI to sign an activation
   certificate.
4. Transfer the signed commercial license and certificate back and import them together.

Import verifies both signatures, schemas, validity, revocation, customer/license correspondence, activation policy,
server UUID, and local public-key fingerprint. Copying only the artifacts to an independently initialized server fails.
Same-customer renewal can reuse identity and activation when policy permits; current-period usage remains. Reduced
limits block new allocation until utilization falls below the new limit.

Switching an existing license-only installation from `false` to `true` makes runtime authorization recovery-required until
the customer generates an activation request and reimports the license with a valid activation certificate. Disabling
activation removes installation binding and therefore permits a valid signed license to be copied to independently
initialized License Servers; it does not disable commercial-license signature, expiry, entitlement, lease, metering,
trusted-time, audit, or state-integrity checks within each server.
