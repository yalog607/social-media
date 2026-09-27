-- ALOUTE V6: cho phép chủ hồ sơ bật/tắt việc người khác phóng to ảnh đại diện/ảnh bìa của mình

ALTER TABLE profiles ADD COLUMN photo_zoom_enabled BOOLEAN NOT NULL DEFAULT true;
