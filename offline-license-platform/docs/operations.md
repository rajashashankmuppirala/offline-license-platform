# Operations

Run one Java License Server process against one local SQLite file and a separate identity directory. Keep the database and identity on durable local storage with OS access controls. Only one server process may own a POC database. Do not place the database inside an application database or couple its lifecycle to a product installation.

Routine backup uses SQLite `VACUUM INTO` under the server write coordinator, refuses overwrite, verifies integrity, and excludes the identity private key. Keep the identity and trusted checkpoint in the protected host backup procedure, separately from routine database exports. Restore only after stopping the process, validate database integrity and identity binding, preserve the previous files for rollback, then restart. A stale database compared with the signed checkpoint requires reconciliation.

Migration to new hardware intentionally creates a new identity and uses offline reactivation. Full state deletion starts UNACTIVATED. Expose only internal HTTPS, protect the admin token, rotate it through local configuration, and restrict filesystem permissions.
