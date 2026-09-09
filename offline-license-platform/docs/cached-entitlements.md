# Cached Entitlements

An activated server may issue an RFC-8785/Ed25519 signed certificate bound to license ID, server ID, instance ID, and instance-key fingerprint. It includes products, features, allowed offline operations, issue time, and expiry. Lifetime is at most 24 hours and never beyond subscription grace.

The product pins the server public key obtained through trusted bootstrap. Product and feature checks may use a valid cache when the server is unavailable. New environment, named-user, agent registration, user login, agent execution, and AEU consumption require central authority. An already acquired lease remains valid only until its signed/persisted absolute expiry. The POC has zero offline AEU allowance.
