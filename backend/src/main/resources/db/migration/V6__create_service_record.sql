-- One visit's work on one piece of equipment
CREATE TABLE service_record (
    id               BIGSERIAL PRIMARY KEY,
    equipment_id     BIGINT        NOT NULL REFERENCES equipment (id) ON DELETE RESTRICT,
    service_date     DATE          NOT NULL,
    kind             VARCHAR(20)   NOT NULL,
    work_done        VARCHAR(4000) NOT NULL,
    parts_used       VARCHAR(2000),
    notes            VARCHAR(4000),
    follow_up_needed BOOLEAN       NOT NULL DEFAULT false,
    follow_up_note   VARCHAR(500),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT service_record_kind_check CHECK (kind IN ('TUNE_UP', 'REPAIR', 'INSTALLATION', 'INSPECTION', 'OTHER'))
);

-- Service history (S6) and "last tune-up" lookups (S10) read newest first per unit
CREATE INDEX service_record_equipment_date_idx ON service_record (equipment_id, service_date DESC, id DESC);
