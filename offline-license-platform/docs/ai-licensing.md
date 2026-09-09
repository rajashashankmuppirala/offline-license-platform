# AI Licensing

AI is independent from human seats. The server enforces enabled capabilities, a global registered-agent limit, and
global concurrent execution leases. Agent crashes release capacity through the same absolute-expiry rule used for users.

The initial generic meter is AI_EXECUTION_UNIT (AEU), reset at deterministic UTC monthly boundaries with no rollover.
Suggested product-policy weights are interactive request 1, autonomous run 5, tool invocation 1, and scheduled run 5.
The metering engine sees only meter ID and amount. Consumption is atomic and idempotent by request ID across all
environments; usage already consumed survives renewal.
