ALTER TABLE members ADD COLUMN authentication_generation BIGINT NOT NULL DEFAULT 0;

CREATE TABLE password_recovery_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    credential_hash VARCHAR(100) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    consumed BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_recovery_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_recovery_member FOREIGN KEY (member_id) REFERENCES members(id)
);
CREATE INDEX idx_recovery_member ON password_recovery_tokens(member_id);

CREATE TABLE password_recovery_limits (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    limit_key VARCHAR(80) NOT NULL,
    window_start DATETIME(6) NOT NULL,
    attempts INT NOT NULL,
    next_allowed_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_recovery_limit_key UNIQUE (limit_key)
);
