ALTER TABLE member_policy_consents ADD COLUMN document_code VARCHAR(64);
ALTER TABLE member_policy_consents ADD COLUMN document_version VARCHAR(64);
ALTER TABLE member_policy_consents ADD COLUMN content_hash VARCHAR(64);

UPDATE member_policy_consents SET
    document_code = (SELECT document_code FROM policy_documents WHERE id = member_policy_consents.document_id),
    document_version = (SELECT document_version FROM policy_documents WHERE id = member_policy_consents.document_id),
    content_hash = (SELECT content_hash FROM policy_documents WHERE id = member_policy_consents.document_id);

ALTER TABLE member_policy_consents MODIFY COLUMN document_code VARCHAR(64) NOT NULL;
ALTER TABLE member_policy_consents MODIFY COLUMN document_version VARCHAR(64) NOT NULL;
ALTER TABLE member_policy_consents MODIFY COLUMN content_hash VARCHAR(64) NOT NULL;
ALTER TABLE member_policy_consents MODIFY COLUMN document_id BIGINT NULL;
ALTER TABLE member_policy_consents ADD CONSTRAINT uk_member_policy_version UNIQUE (member_id, document_code, document_version);
