ALTER TABLE payments ADD COLUMN notification_confirmed_at DATETIME(6);
ALTER TABLE payment_cancellations ADD COLUMN notification_confirmed_at DATETIME(6);
ALTER TABLE shipment_events ADD COLUMN notification_confirmed_at DATETIME(6);
CREATE INDEX idx_payment_notification ON payments(notification_confirmed_at);
CREATE INDEX idx_cancellation_notification ON payment_cancellations(notification_confirmed_at);
CREATE INDEX idx_shipping_notification ON shipment_events(notification_confirmed_at);

CREATE TABLE order_notifications (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    order_number VARCHAR(64) NOT NULL,
    event_type VARCHAR(24) NOT NULL,
    confirmed_at DATETIME(6) NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempt_count INT NOT NULL,
    cycle_attempts INT NOT NULL,
    failure_code VARCHAR(32),
    failed_at DATETIME(6),
    read_at DATETIME(6),
    CONSTRAINT uk_notification_event UNIQUE (order_id, event_type),
    CONSTRAINT fk_notification_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_notification_member FOREIGN KEY (member_id) REFERENCES members(id)
);
CREATE INDEX idx_notification_owner ON order_notifications(member_id, status, confirmed_at, id);
CREATE INDEX idx_notification_work ON order_notifications(status, id);
