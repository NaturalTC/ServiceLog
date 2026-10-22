-- Requests sent with an Idempotency-Key header (S21). The phone keeps actions taken without
-- signal and sends them later; if the signal drops after the server saved one but before
-- the phone heard back, it sends it again. The stored reply is returned instead of saving
-- the same service record twice. Rows are kept for a week.
CREATE TABLE idempotent_request (
    idempotency_key VARCHAR(80)  PRIMARY KEY,
    username        VARCHAR(255) NOT NULL,
    method          VARCHAR(10)  NOT NULL,
    path            VARCHAR(300) NOT NULL,
    -- 0 while the first request is still being handled
    status          INT          NOT NULL,
    content_type    VARCHAR(100),
    body            TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idempotent_request_created_idx ON idempotent_request (created_at);
