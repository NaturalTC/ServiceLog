-- A scheduled visit to a property. Times are local business time (see app.time-zone),
-- since the owner works in one place and thinks in local time.
CREATE TABLE job (
    id               BIGSERIAL PRIMARY KEY,
    property_id      BIGINT        NOT NULL REFERENCES property (id) ON DELETE RESTRICT,
    scheduled_date   DATE          NOT NULL,
    start_time       TIME          NOT NULL,
    duration_minutes INTEGER       NOT NULL,
    type             VARCHAR(20)   NOT NULL,
    urgency          VARCHAR(10)   NOT NULL,
    status           VARCHAR(15)   NOT NULL DEFAULT 'SCHEDULED',
    notes            VARCHAR(2000),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT job_type_check CHECK (type IN ('INSTALL', 'SERVICE_CALL', 'TUNE_UP', 'ESTIMATE_VISIT')),
    CONSTRAINT job_urgency_check CHECK (urgency IN ('NORMAL', 'URGENT')),
    CONSTRAINT job_status_check CHECK (status IN ('SCHEDULED', 'CANCELLED')),
    CONSTRAINT job_duration_check CHECK (duration_minutes BETWEEN 15 AND 720)
);

-- The schedule is always read by date range, in time order
CREATE INDEX job_schedule_idx ON job (scheduled_date, start_time);
CREATE INDEX job_property_idx ON job (property_id);
