CREATE TABLE cart_items (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_cart_member_product UNIQUE (member_id, product_id),
    CONSTRAINT fk_cart_member FOREIGN KEY (member_id) REFERENCES members(id),
    CONSTRAINT fk_cart_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT ck_cart_quantity CHECK (quantity > 0)
);
CREATE TABLE cart_additions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    request_key VARCHAR(36) NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    CONSTRAINT uk_cart_addition_key UNIQUE (member_id, request_key),
    CONSTRAINT fk_cart_addition_member FOREIGN KEY (member_id) REFERENCES members(id),
    CONSTRAINT ck_cart_addition_quantity CHECK (quantity > 0)
);
CREATE TABLE cart_checkouts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE,
    member_id BIGINT NOT NULL,
    state VARCHAR(16) NOT NULL,
    CONSTRAINT fk_cart_checkout_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_cart_checkout_member FOREIGN KEY (member_id) REFERENCES members(id)
);
CREATE INDEX ix_cart_cleanup_ready ON cart_checkouts(state, id);
CREATE TABLE cart_checkout_items (
    checkout_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    item_version BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit_price BIGINT NOT NULL,
    outcome VARCHAR(24) NOT NULL,
    CONSTRAINT uk_cart_checkout_item UNIQUE (checkout_id, item_id),
    CONSTRAINT fk_cart_snapshot_checkout FOREIGN KEY (checkout_id) REFERENCES cart_checkouts(id),
    CONSTRAINT ck_cart_snapshot_quantity CHECK (quantity > 0)
);
