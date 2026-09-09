# User Licensing

Named assignments count HUMAN accounts only and are global across environments unless a future license explicitly defines pools. Category limits and product limits are independently enforced. Service and system accounts are classified explicitly and are not silently treated as humans. Released assignments retain a 30-day reassignment cooldown; audited override is limited to employee departure or assignment correction.

Concurrent seats use five-minute leases with one-minute heartbeat by default. Acquisition is an atomic customer-wide check across PROD, UAT, QA, DEV, DR, and OTHER. Browser or application crashes need no decrement: an expired lease stops counting. Restart preserves absolute expiry. The client heartbeat manager never extends a lease locally during an outage.
