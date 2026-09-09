# License Server Identity

First startup creates a UUID and Ed25519 key pair in a configured identity directory with owner-only file permissions. Restarts load and self-test the same key. Empty, corrupt, mismatched, or missing identity fails closed; it is never silently regenerated over existing licensed state.

Routine SQLite backups exclude the identity key. Restore is valid only on the original installation with its matching identity and signed trusted-time checkpoint. New hardware, lost identity, or deliberate reset creates a new identity and requires offline activation. Copying license and activation files alone is prevented. Copying an entire VM, including all software-held secrets and state, remains a documented residual risk for this simple POC.
