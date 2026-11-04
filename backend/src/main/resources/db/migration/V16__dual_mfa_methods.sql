ALTER TABLE users DROP CONSTRAINT users_mfa_method_check;
ALTER TABLE users ADD CONSTRAINT users_mfa_method_check
    CHECK (mfa_method IN ('EMAIL', 'TOTP', 'BOTH'));

ALTER TABLE email_mfa_challenges DROP CONSTRAINT email_mfa_challenges_purpose_check;
ALTER TABLE email_mfa_challenges ADD CONSTRAINT email_mfa_challenges_purpose_check
    CHECK (purpose IN ('LOGIN', 'LOGIN_SELECTION', 'ENROLL', 'STEP_UP'));

ALTER TABLE email_mfa_challenges ADD COLUMN method varchar(10)
    CHECK (method IN ('EMAIL', 'TOTP'));
