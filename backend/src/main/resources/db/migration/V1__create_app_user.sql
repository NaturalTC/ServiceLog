-- The app has one account: the shop owner. No public sign-up.
CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Emails are compared case-insensitively, so enforce uniqueness the same way
CREATE UNIQUE INDEX app_user_email_lower_idx ON app_user (lower(email));
