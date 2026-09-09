# License Format

Commercial licenses use immutable typed schema version `1.0`. Stable identifiers cover customers, products, features, environments, user categories, AI capabilities, meters, and accounting periods. Legacy fields map explicitly: Concurrent to `maxConcurrentUsers`, named and role counts to user limits, product names to stable product IDs, and Validator sub-products to TABLEAU, ORACLE_ANALYTICS, and COGNOS. Legacy input does not silently grant AI or environments.

A signed envelope contains envelope version, artifact type, payload bytes, algorithm, key identifier, and signature. Unsupported versions, malformed payloads, unknown artifact types, or schema versions are rejected before entitlement evaluation.
