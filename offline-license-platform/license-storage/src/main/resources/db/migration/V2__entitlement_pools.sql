ALTER TABLE user_lease ADD COLUMN pool_id TEXT NOT NULL DEFAULT 'CUSTOMER_WIDE';
ALTER TABLE registered_agent ADD COLUMN pool_id TEXT NOT NULL DEFAULT 'CUSTOMER_WIDE';
ALTER TABLE agent_lease ADD COLUMN pool_id TEXT NOT NULL DEFAULT 'CUSTOMER_WIDE';
ALTER TABLE usage_consumption ADD COLUMN pool_id TEXT NOT NULL DEFAULT 'CUSTOMER_WIDE';

ALTER TABLE meter_usage RENAME TO meter_usage_legacy;
CREATE TABLE meter_usage (
    customer_id TEXT NOT NULL,
    pool_id TEXT NOT NULL DEFAULT 'CUSTOMER_WIDE',
    meter_id TEXT NOT NULL,
    period_start TEXT NOT NULL,
    period_end TEXT NOT NULL,
    used_amount INTEGER NOT NULL CHECK (used_amount >= 0),
    licensed_limit INTEGER NOT NULL CHECK (licensed_limit >= 0),
    PRIMARY KEY(customer_id, pool_id, meter_id, period_start)
);
INSERT INTO meter_usage(customer_id,pool_id,meter_id,period_start,period_end,used_amount,licensed_limit)
SELECT customer_id,'CUSTOMER_WIDE',meter_id,period_start,period_end,used_amount,licensed_limit FROM meter_usage_legacy;
DROP TABLE meter_usage_legacy;
