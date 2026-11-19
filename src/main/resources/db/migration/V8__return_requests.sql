CREATE TABLE return_requests (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    active_order_id BIGINT,
    request_key VARCHAR(36) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    policy_version VARCHAR(64) NOT NULL,
    policy_guidance TEXT NOT NULL,
    resellable BOOLEAN,
    refund_eligible BOOLEAN,
    cancellation_id BIGINT,
    revision BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_return_request_key UNIQUE (order_id, request_key),
    CONSTRAINT uk_return_active_order UNIQUE (active_order_id),
    CONSTRAINT fk_return_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_return_active_order FOREIGN KEY (active_order_id) REFERENCES orders (id),
    CONSTRAINT fk_return_cancellation FOREIGN KEY (cancellation_id) REFERENCES payment_cancellations (id),
    CONSTRAINT chk_return_active CHECK (active_order_id IS NULL OR active_order_id = order_id)
);
CREATE INDEX idx_return_status_created ON return_requests (status, created_at, id);

CREATE TABLE return_request_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    return_request_id BIGINT NOT NULL,
    request_key VARCHAR(36) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    action VARCHAR(32) NOT NULL,
    operator_id BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    recorded_at DATETIME(6) NOT NULL,
    cancellation_id BIGINT,
    CONSTRAINT uk_return_action_key UNIQUE (return_request_id, request_key),
    CONSTRAINT fk_return_event_request FOREIGN KEY (return_request_id) REFERENCES return_requests (id),
    CONSTRAINT fk_return_event_cancellation FOREIGN KEY (cancellation_id) REFERENCES payment_cancellations (id)
);
