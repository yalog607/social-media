-- ALOUTE V53: cho phép chủ hồ sơ bật/tắt việc người khác xem bạn bè và số người mình đang theo dõi

ALTER TABLE profiles ADD COLUMN friends_list_visible BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE profiles ADD COLUMN following_list_visible BOOLEAN NOT NULL DEFAULT true;
