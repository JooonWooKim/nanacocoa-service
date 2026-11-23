ALTER TABLE support_inquiries ADD COLUMN notification_confirmed_at DATETIME(6);
CREATE INDEX idx_support_notification ON support_inquiries(notification_confirmed_at, id);

ALTER TABLE order_notifications MODIFY COLUMN order_id BIGINT NULL;
ALTER TABLE order_notifications MODIFY COLUMN order_number VARCHAR(64) NULL;
ALTER TABLE order_notifications ADD COLUMN inquiry_id BIGINT NULL;
ALTER TABLE order_notifications ADD CONSTRAINT fk_notification_inquiry FOREIGN KEY (inquiry_id) REFERENCES support_inquiries(id);
ALTER TABLE order_notifications ADD CONSTRAINT uk_notification_inquiry UNIQUE (inquiry_id);
ALTER TABLE order_notifications ADD CONSTRAINT ck_notification_subject CHECK (
    (event_type = 'SUPPORT_ANSWERED' AND inquiry_id IS NOT NULL AND order_id IS NULL AND order_number IS NULL)
    OR
    (event_type <> 'SUPPORT_ANSWERED' AND inquiry_id IS NULL AND order_id IS NOT NULL AND order_number IS NOT NULL)
);
