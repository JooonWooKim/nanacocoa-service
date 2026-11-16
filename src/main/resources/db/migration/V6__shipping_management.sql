ALTER TABLE orders ADD COLUMN legacy_shipping_review BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE orders SET legacy_shipping_review = TRUE;

CREATE TABLE shipments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    status VARCHAR(32),
    carrier_code VARCHAR(32),
    tracking_number VARCHAR(64),
    shipped_at DATETIME(6),
    delivered_at DATETIME(6),
    revision BIGINT NOT NULL,
    CONSTRAINT uk_shipments_order UNIQUE (order_id),
    CONSTRAINT fk_shipments_order FOREIGN KEY (order_id) REFERENCES orders (id)
);

CREATE TABLE shipment_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    shipment_id BIGINT NOT NULL,
    request_key VARCHAR(36) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    action VARCHAR(32) NOT NULL,
    previous_status VARCHAR(32) NOT NULL,
    next_status VARCHAR(32) NOT NULL,
    previous_carrier VARCHAR(32),
    previous_tracking VARCHAR(64),
    next_carrier VARCHAR(32),
    next_tracking VARCHAR(64),
    operator_id BIGINT NOT NULL,
    recorded_at DATETIME(6) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    CONSTRAINT uk_shipment_event_request UNIQUE (shipment_id, request_key),
    CONSTRAINT fk_shipment_events_shipment FOREIGN KEY (shipment_id) REFERENCES shipments (id)
);
