# Local Java Deployment

Prerequisites are Java 21 and Maven 3.9 only. Build dependencies and the SQLite native library must be staged before
entering an air gap. Runtime requires no Internet, external database, Docker, or container engine.

Configure the SQLite file, identity directory, signed trusted-time checkpoint, vendor public key, administrator token,
loopback/internal bind address, and TLS keystore through environment variables in `application.yml`. Generate a
customer-PKI PKCS#12 keystore and set TLS enabled for any non-loopback deployment. Start the executable Spring Boot JAR
under systemd, launchd, or the customer's service manager using a dedicated OS account and restrictive directories.

Readiness and liveness are under `/actuator/health`. Back up through the supplied coordinated script/API rather than
copying a live database file. Do not co-locate this SQLite file with any licensed application's database.
