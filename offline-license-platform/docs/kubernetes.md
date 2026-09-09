# Kubernetes Integration

All replicas in one logical product deployment read one protected durable identity file. For the POC this may be mounted
from a customer-managed persistent volume or carefully controlled Secret. A Secret makes export possible and therefore
is not hardware-rooted identity. Pods never generate independent identities.

Scaling 3 to 20 replicas, rolling updates, and rescheduling do not change environment consumption. Namespace recreation
must restore the identity from the customer's protected installation-state backup. If it is lost, an administrator
decommissions the old registration and bootstraps a new one. The License Server can be reached across clusters through
any internally routable HTTPS address.

The container-free integration proof submits a real registration request for every simulated replica to the
authoritative registration service and SQLite repository. It verifies scale-up, rollout, rescheduling, restoration, and
the lost-identity denial; comparing identity strings alone is not considered proof of licensed behavior.
