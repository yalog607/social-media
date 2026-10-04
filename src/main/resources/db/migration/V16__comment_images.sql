-- ALOUTE V16: bình luận có thể kèm một ảnh (dán Ctrl+V hoặc chọn file). Bình luận chỉ có ảnh thì content là chuỗi rỗng.
ALTER TABLE comments ADD COLUMN image_url VARCHAR(500);
