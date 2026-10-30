ALTER TABLE comments ADD COLUMN sticker_id VARCHAR(80) REFERENCES stickers(id);
