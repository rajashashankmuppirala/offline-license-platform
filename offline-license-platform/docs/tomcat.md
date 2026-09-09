# Tomcat Integration

Persist the instance identity in an application data directory outside the WAR and exploded webapp. A servlet lifecycle
adapter should load it on startup, register idempotently, create per-session user lease managers after authentication,
and close them on logout/session destruction. Shutdown release is helpful but correctness relies on absolute expiry.
Redeployment and Tomcat restart reuse the same logical instance.
