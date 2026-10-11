CREATE TABLE google_identities (
    user_id bigint PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    google_sub varchar(255) NOT NULL UNIQUE,
    linked_at timestamptz NOT NULL DEFAULT now()
);
