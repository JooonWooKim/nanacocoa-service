CREATE TABLE shipping_addresses (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    label VARCHAR(80) NOT NULL,
    recipient_name VARCHAR(100) NOT NULL,
    phone_number VARCHAR(30) NOT NULL,
    postal_code VARCHAR(5) NOT NULL,
    basic_address VARCHAR(500) NOT NULL,
    detail_address VARCHAR(500) NOT NULL,
    CONSTRAINT uk_address_owner_id UNIQUE (member_id, id),
    CONSTRAINT fk_address_member FOREIGN KEY (member_id) REFERENCES members(id)
);
CREATE TABLE shipping_address_books (
    member_id BIGINT NOT NULL PRIMARY KEY,
    revision BIGINT NOT NULL DEFAULT 0,
    default_address_id BIGINT NULL,
    CONSTRAINT fk_address_book_member FOREIGN KEY (member_id) REFERENCES members(id),
    CONSTRAINT fk_address_book_default FOREIGN KEY (member_id, default_address_id)
        REFERENCES shipping_addresses(member_id, id)
);
CREATE TABLE shipping_address_registrations (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    request_key VARCHAR(36) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    CONSTRAINT uk_address_registration_key UNIQUE (member_id, request_key),
    CONSTRAINT fk_address_registration_member FOREIGN KEY (member_id) REFERENCES members(id)
);
