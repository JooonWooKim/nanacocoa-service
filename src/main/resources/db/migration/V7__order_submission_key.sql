ALTER TABLE orders ADD COLUMN submission_key VARCHAR(36);
ALTER TABLE orders ADD CONSTRAINT uk_orders_member_submission UNIQUE (member_id, submission_key);
