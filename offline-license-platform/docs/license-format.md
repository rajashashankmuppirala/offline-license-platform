# License Format

The published [commercial license JSON schema](contracts/commercial-license.schema.json) is shipped with the application
documentation. Local planning folders (`specs/`, `.specify/`, and `.specs/`) are excluded from Git; the build does not depend
on them. The contract test verifies that the published schema matches the bundled runtime schema byte for byte.

Commercial licenses use immutable typed schema version `1.0`. Stable identifiers cover customers, products, features,
environments, user categories, AI capabilities, meters, and accounting periods. Legacy fields map explicitly: Concurrent
to `maxConcurrentUsers`, named and role counts to user limits, product names to stable product IDs, and Validator
sub-products to TABLEAU, ORACLE_ANALYTICS, and COGNOS. Legacy input does not silently grant AI or environments.

A signed envelope contains envelope version, artifact type, payload bytes, algorithm, key identifier, and signature.
Unsupported versions, malformed payloads, unknown artifact types, or schema versions are rejected before entitlement
evaluation.
