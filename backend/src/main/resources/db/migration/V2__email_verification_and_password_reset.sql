ALTER TABLE app_user
    ADD COLUMN email_verified_at TIMESTAMPTZ,
    -- Every login token carries this number. Changing the password increments it,
    -- which makes all older tokens invalid: a password reset logs out every session.
    ADD COLUMN session_version   INTEGER NOT NULL DEFAULT 0;

-- Single-use links for email verification and password reset.
-- Only a SHA-256 hash of each token is stored, so a database leak can't be used to reset a password.
CREATE TABLE auth_token (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    purpose    VARCHAR(30)  NOT NULL,
    token_hash VARCHAR(64)  NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX auth_token_user_purpose_idx ON auth_token (user_id, purpose);
