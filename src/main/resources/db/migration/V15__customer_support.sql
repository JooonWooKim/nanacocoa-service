CREATE TABLE support_inquiries (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    order_id BIGINT NULL,
    request_key VARCHAR(36) NOT NULL,
    title VARCHAR(100) NOT NULL,
    body VARCHAR(2000) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    reply_body VARCHAR(2000) NULL,
    replied_by BIGINT NULL,
    replied_at DATETIME(6) NULL,
    CONSTRAINT uk_support_member_request UNIQUE (member_id, request_key),
    CONSTRAINT fk_support_member FOREIGN KEY (member_id) REFERENCES members(id),
    CONSTRAINT fk_support_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_support_operator FOREIGN KEY (replied_by) REFERENCES members(id)
);
CREATE INDEX idx_support_member_page ON support_inquiries(member_id, created_at, id);
CREATE INDEX idx_support_status_page ON support_inquiries(status, created_at, id);
CREATE INDEX idx_support_page ON support_inquiries(created_at, id);
