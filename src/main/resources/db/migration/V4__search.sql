-- ALOUTE V4: tìm kiếm người dùng

-- Tên để tìm: username + tên hiển thị, đã bỏ dấu và viết thường (Profile#searchName tự cập nhật khi lưu).
-- Cột thêm với giá trị mặc định ''; các hàng có sẵn được điền lại một lần bởi SearchNameBackfill khi khởi động.
ALTER TABLE profiles ADD COLUMN search_name TEXT NOT NULL DEFAULT '';

CREATE INDEX ix_profiles_search ON profiles USING gin (search_name gin_trgm_ops);
