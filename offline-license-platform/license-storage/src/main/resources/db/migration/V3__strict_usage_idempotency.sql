ALTER TABLE usage_consumption ADD COLUMN action_type TEXT;
ALTER TABLE usage_consumption ADD COLUMN period_end TEXT;
ALTER TABLE usage_consumption ADD COLUMN licensed_limit INTEGER;
ALTER TABLE usage_consumption ADD COLUMN usage_after INTEGER;
ALTER TABLE usage_consumption ADD COLUMN reason_code TEXT;
