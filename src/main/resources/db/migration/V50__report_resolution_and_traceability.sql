-- V50: Bổ sung thông tin xử lý và truy vết báo cáo vi phạm
ALTER TABLE reports ADD COLUMN IF NOT EXISTS action VARCHAR(20);
ALTER TABLE reports ADD COLUMN IF NOT EXISTS handling_note VARCHAR(500);
ALTER TABLE reports ADD COLUMN IF NOT EXISTS target_owner_id UUID REFERENCES users (id);
ALTER TABLE reports ADD COLUMN IF NOT EXISTS content_snapshot TEXT;

CREATE INDEX IF NOT EXISTS ix_reports_handled ON reports (status, handled_at DESC);
CREATE INDEX IF NOT EXISTS ix_reports_target ON reports (target_type, target_id);
