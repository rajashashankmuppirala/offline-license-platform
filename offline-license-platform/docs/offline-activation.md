# Offline Activation

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
