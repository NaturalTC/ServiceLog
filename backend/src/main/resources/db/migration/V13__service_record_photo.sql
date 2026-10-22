-- Photos on a service record (S22). The image files live in photo storage (a folder on
-- disk, a Docker volume in production); this table records which belong to which record.
CREATE TABLE service_record_photo (
    id                BIGSERIAL PRIMARY KEY,
    service_record_id BIGINT       NOT NULL REFERENCES service_record (id) ON DELETE CASCADE,
    -- File names in storage: the full-size image and its thumbnail
    storage_key       VARCHAR(100) NOT NULL UNIQUE,
    thumb_key         VARCHAR(100) NOT NULL UNIQUE,
    width             INT          NOT NULL,
    height            INT          NOT NULL,
    size_bytes        INT          NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX service_record_photo_record_idx ON service_record_photo (service_record_id);
