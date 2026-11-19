CREATE TABLE policy_documents (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    document_code VARCHAR(64) NOT NULL,
    document_version VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    body LONGTEXT NOT NULL,
    consent_mode VARCHAR(32) NOT NULL,
    effective_at DATETIME(6) NOT NULL,
    approved_by VARCHAR(100) NOT NULL,
    approved_at DATETIME(6) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    active BOOLEAN NOT NULL,
    CONSTRAINT uk_policy_document_version UNIQUE (document_code, document_version)
);
CREATE TABLE policy_publications (
    id BIGINT NOT NULL PRIMARY KEY,
    revision BIGINT NOT NULL,
    content_hash VARCHAR(64) NOT NULL
);
INSERT INTO policy_publications (id, revision, content_hash) VALUES (1, 0, '');
CREATE TABLE member_policy_consents (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    agreed BOOLEAN NOT NULL,
    recorded_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_member_policy_consent UNIQUE (member_id, document_id),
    CONSTRAINT fk_consent_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_consent_document FOREIGN KEY (document_id) REFERENCES policy_documents (id)
);
