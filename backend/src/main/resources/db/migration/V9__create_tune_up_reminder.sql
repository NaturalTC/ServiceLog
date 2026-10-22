-- Every tune-up reminder email the owner sends (or tried to send), for the record
CREATE TABLE tune_up_reminder (
    id              BIGSERIAL PRIMARY KEY,
    customer_id     BIGINT        NOT NULL REFERENCES customer (id) ON DELETE CASCADE,
    property_id     BIGINT        NOT NULL REFERENCES property (id) ON DELETE CASCADE,
    recipient_email VARCHAR(254)  NOT NULL,
    subject         VARCHAR(200)  NOT NULL,
    body            TEXT          NOT NULL,
    status          VARCHAR(10)   NOT NULL,
    error           VARCHAR(500),
    sent_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT tune_up_reminder_status_check CHECK (status IN ('SENT', 'FAILED'))
);

CREATE INDEX tune_up_reminder_property_idx ON tune_up_reminder (property_id, sent_at DESC);
CREATE INDEX tune_up_reminder_customer_idx ON tune_up_reminder (customer_id, sent_at DESC);
