-- Migration V33: Bổ sung cột emoji vào bảng stories phục vụ ghi chú gắn cảm xúc
ALTER TABLE stories ADD COLUMN IF NOT EXISTS emoji VARCHAR(16);

