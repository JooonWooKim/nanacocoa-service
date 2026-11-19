-- 운영자가 승인한 읽기 전용 연결에서 실행한다. 회원·동의·접속 자격 증명은 내보내지 않는다.
-- mysql --batch --raw --skip-column-names ... < scripts/policy-archive-export.sql > private-policy-export.json
SELECT COALESCE(JSON_ARRAYAGG(JSON_OBJECT(
    'documentCode', document_code, 'version', document_version,
    'title', title, 'body', body, 'consentMode', consent_mode,
    'effectiveAt', CONCAT(DATE_FORMAT(effective_at, '%Y-%m-%dT%H:%i:%s.%f'), 'Z'),
    'approvedBy', approved_by,
    'approvedAt', CONCAT(DATE_FORMAT(approved_at, '%Y-%m-%dT%H:%i:%s.%f'), 'Z'),
    'contentHash', content_hash
)), JSON_ARRAY()) FROM policy_documents;
