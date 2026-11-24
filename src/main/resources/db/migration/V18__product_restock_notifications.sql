ALTER TABLE products ADD COLUMN availability_epoch BIGINT NOT NULL DEFAULT 0;

CREATE TABLE product_restock_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    availability_epoch BIGINT NOT NULL,
    confirmed_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_restock_event_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT uk_restock_event_epoch UNIQUE (product_id, availability_epoch)
);

CREATE TABLE product_restock_subscriptions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    subscribed_epoch BIGINT NOT NULL,
    active BOOLEAN,
    CONSTRAINT fk_restock_subscription_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_restock_subscription_member FOREIGN KEY (member_id) REFERENCES members(id),
    CONSTRAINT uk_restock_subscription_active UNIQUE (product_id, member_id, active)
);
CREATE INDEX idx_restock_subscriptions_active ON product_restock_subscriptions(active, product_id, id);

ALTER TABLE order_notifications ADD COLUMN restock_subscription_id BIGINT NULL;
ALTER TABLE order_notifications ADD COLUMN product_id BIGINT NULL;
ALTER TABLE order_notifications ADD CONSTRAINT fk_notification_restock_subscription FOREIGN KEY (restock_subscription_id) REFERENCES product_restock_subscriptions(id);
ALTER TABLE order_notifications ADD CONSTRAINT fk_notification_restock_product FOREIGN KEY (product_id) REFERENCES products(id);
ALTER TABLE order_notifications ADD CONSTRAINT uk_notification_restock_subscription UNIQUE (restock_subscription_id);
ALTER TABLE order_notifications DROP CONSTRAINT ck_notification_subject;
ALTER TABLE order_notifications ADD CONSTRAINT ck_notification_subject CHECK (
    (event_type = 'SUPPORT_ANSWERED' AND inquiry_id IS NOT NULL AND order_id IS NULL AND order_number IS NULL
        AND restock_subscription_id IS NULL AND product_id IS NULL)
    OR
    (event_type = 'PRODUCT_RESTOCKED' AND inquiry_id IS NULL AND order_id IS NULL AND order_number IS NULL
        AND restock_subscription_id IS NOT NULL AND product_id IS NOT NULL)
    OR
    (event_type IN ('PAYMENT_COMPLETED','SHIPPED','REFUND_COMPLETED') AND inquiry_id IS NULL AND order_id IS NOT NULL
        AND order_number IS NOT NULL AND restock_subscription_id IS NULL AND product_id IS NULL)
);
