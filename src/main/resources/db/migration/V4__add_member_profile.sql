ALTER TABLE members
  ADD COLUMN address VARCHAR(500) NOT NULL DEFAULT '';

ALTER TABLE members
  ADD COLUMN phone_number VARCHAR(13) NOT NULL DEFAULT '';

ALTER TABLE members
  ADD COLUMN email_receive_consent VARCHAR(1) NOT NULL DEFAULT 'N';

ALTER TABLE members
  ADD CONSTRAINT chk_members_email_receive_consent
  CHECK (email_receive_consent IN ('Y', 'N'));
