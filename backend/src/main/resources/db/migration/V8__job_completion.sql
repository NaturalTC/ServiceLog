-- Jobs can be started and finished; finishing one creates service records
ALTER TABLE job DROP CONSTRAINT job_status_check;
ALTER TABLE job ADD CONSTRAINT job_status_check
    CHECK (status IN ('SCHEDULED', 'IN_PROGRESS', 'DONE', 'CANCELLED'));
ALTER TABLE job ADD COLUMN completed_at TIMESTAMPTZ;

-- Which job a service record came from (null for records entered directly)
ALTER TABLE service_record ADD COLUMN job_id BIGINT REFERENCES job (id) ON DELETE SET NULL;
CREATE INDEX service_record_job_idx ON service_record (job_id);
