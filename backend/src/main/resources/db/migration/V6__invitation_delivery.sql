-- Keep invitation acceptance separate from replacement and SMTP delivery failures.
ALTER TABLE staff_invitations
    ADD COLUMN revoked_at timestamptz,
    ADD COLUMN delivery_status varchar(12) NOT NULL DEFAULT 'SENT'
        CHECK (delivery_status IN ('PENDING', 'SENT', 'FAILED'));

CREATE INDEX staff_invitations_pending_email_idx
    ON staff_invitations(email, id DESC)
    WHERE accepted_at IS NULL AND revoked_at IS NULL;
