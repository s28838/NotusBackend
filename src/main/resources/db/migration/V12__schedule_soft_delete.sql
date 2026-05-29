ALTER TABLE schedule
    ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS recurrence_series_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS repeat_every_weeks INTEGER,
    ADD COLUMN IF NOT EXISTS recurrence_ends_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_schedule_deleted ON schedule(deleted);
CREATE INDEX IF NOT EXISTS idx_schedule_recurrence_series_id ON schedule(recurrence_series_id);
