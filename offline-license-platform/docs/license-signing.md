# License Signing

All payloads are canonicalized with RFC 8785 and signed with Ed25519 through Java cryptographic APIs. The signature
input includes a versioned artifact-type domain separator, preventing a payload signed for one purpose from being
accepted as another. Public keys use X.509 encoding, private keys use PKCS#8, and fingerprints are SHA-256 over encoded
public-key bytes.

Vendor signing APIs live only in the vendor crypto artifact and issuer CLI. The server and Java client depend only on
verification and local-identity signing APIs. There is no default private key. Production issuance should use an HSM or
enterprise signing service.
