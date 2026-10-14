ALTER TABLE users ADD COLUMN mfa_method varchar(10) NOT NULL DEFAULT 'EMAIL'
    CHECK (mfa_method IN ('EMAIL', 'TOTP'));

ALTER TABLE email_mfa_challenges DROP CONSTRAINT email_mfa_challenges_purpose_check;
ALTER TABLE email_mfa_challenges ADD CONSTRAINT email_mfa_challenges_purpose_check
    CHECK (purpose IN ('LOGIN', 'ENROLL', 'STEP_UP'));

CREATE TABLE totp_credentials (
    user_id bigint PRIMARY KEY REFERENCES users(id),
    encrypted_secret text,
    pending_secret text,
    pending_expires_at timestamptz,
    last_used_step bigint,
    updated_at timestamptz NOT NULL DEFAULT now()
);
