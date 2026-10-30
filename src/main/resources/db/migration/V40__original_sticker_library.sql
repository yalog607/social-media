CREATE TABLE user_sticker_recent (
 user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 sticker_id VARCHAR(80) NOT NULL REFERENCES stickers(id) ON DELETE CASCADE,
 used_at TIMESTAMPTZ NOT NULL,
 PRIMARY KEY (user_id, sticker_id)
);
CREATE TABLE user_sticker_favorites (
 user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 sticker_id VARCHAR(80) NOT NULL REFERENCES stickers(id) ON DELETE CASCADE,
 added_at TIMESTAMPTZ NOT NULL,
 PRIMARY KEY (user_id, sticker_id)
);
INSERT INTO sticker_packs (id,name,description,icon_url,author,total_stickers,is_default,sort_order) VALUES ('aloute-cat','Mèo Mỡ','Bộ biểu cảm mèo mỡ','/stickers/original/characters.png','ALOUTE',8,true,100);
INSERT INTO stickers (id,pack_id,code,name,image_url,sort_order) VALUES ('aloute-cat-0','aloute-cat','atlas-0','Mèo Mỡ · Chào bạn','/stickers/original/characters.png',0);
INSERT INTO stickers (id,pack_id,code,name,image_url,sort_order) VALUES ('aloute-cat-1','aloute-cat','atlas-1','Mèo Mỡ · Thả tim','/stickers/original/characters.png',1);
INSERT INTO stickers (id,pack_id,code,name,image_url,sort_order) VALUES ('aloute-cat-2','aloute-cat','atlas-2','Mèo Mỡ · Cười vui','/stickers/original/characters.png',2);
INSERT INTO stickers (id,pack_id,code,name,image_url,sort_order) VALUES ('aloute-cat-3','aloute-cat','atlas-3','Mèo Mỡ · Khóc nhè','/stickers/original/characters.png',3);
INSERT INTO stickers (id,pack_id,code,name,image_url,sort_order) VALUES ('aloute-cat-4','aloute-cat','atlas-4','Mèo Mỡ · Giận dỗi','/stickers/original/characters.png',4);
INSERT INTO stickers (id,pack_id,code,name,image_url,sort_order) VALUES ('aloute-cat-5','aloute-cat','atlas-5','Mèo Mỡ · Ngạc nhiên','/stickers/original/characters.png',5);
INSERT INTO stickers (id,pack_id,code,name,image_url,sort_order) VALUES ('aloute-cat-6','aloute-cat','atlas-6','Mèo Mỡ · Buồn ngủ','/stickers/original/characters.png',6);
INSERT INTO stickers (id,pack_id,code,name,image_url,sort_order) VALUES ('aloute-cat-7','aloute-cat','atlas-7','Mèo Mỡ · Chăm học','/stickers/original/characters.png',7);
INSERT INTO sticker_packs (id,name,description,icon_url,author,total_stickers,is_default,sort_order) VALUES ('aloute-frog','Ếch Nhỏ','Bộ biểu cảm ếch nhỏ','/stickers/original/characters.png','ALOUTE',8,true,101);
INSERT INTO stickers (id,pack_id,code,name,image_url,sort_order) VALUES ('aloute-frog-0','aloute-frog','atlas-8','Ếch Nhỏ · Chào bạn','/stickers/original/characters.png',0);
INSERT INTO stickers (id,pack_id,code,name,image_url,sort_order) VALUES ('aloute-frog-1','aloute-frog','atlas-9','Ếch Nhỏ · Thả tim','/stickers/original/characters.png',1);
INSERT INTO stickers (id,pack_id,code,name,image_url,sort_order) VALUES ('aloute-frog-2','aloute-frog','atlas-10','Ếch Nhỏ · Cười vui','/stickers/original/characters.png',2);
INSERT INTO stickers (id,pack_id,code,name,image_url,sort_order) VALUES ('aloute-frog-3','aloute-frog','atlas-11','Ếch Nhỏ · Khóc nhè','/stickers/original/characters.png',3);
INSERT INTO stickers (id,pack_id,code,name,image_url,sort_order) VALUES ('aloute-frog-4','aloute-frog','atlas-12','Ếch Nhỏ · Giận dỗi','/stickers/original/characters.png',4);
INSERT INTO stickers (id,pack_id,code,name,image_url,sort_order) VALUES ('aloute-frog-5','aloute-frog','atlas-13','Ếch Nhỏ · Ngạc nhiên','/stickers/original/characters.png',5);
INSERT INTO stickers (id,pack_id,code,name,image_url,sort_order) VALUES ('aloute-frog-6','aloute-frog','atlas-14','Ếch Nhỏ · Buồn ngủ','/stickers/original/characters.png',6);
INSERT INTO stickers (id,pack_id,code,name,image_url,sort_order) VALUES ('aloute-frog-7','aloute-frog','atlas-15','Ếch Nhỏ · Uống trà','/stickers/original/characters.png',7);
INSERT INTO sticker_packs (id,name,description,icon_url,author,total_stickers,is_default,sort_order) VALUES ('aloute-dog','Cún Bông','Bộ biểu cảm cún bông','/stickers/original/characters.png','ALOUTE',8,true,102);
INSERT INTO stickers (id,pack_id,code,name,image_url,sort_order) VALUES ('aloute-dog-0','aloute-dog','atlas-16','Cún Bông · Chào bạn','/stickers/original/characters.png',0);
INSERT INTO stickers (id,pack_id,code,name,image_url,sort_order) VALUES ('aloute-dog-1','aloute-dog','atlas-17','Cún Bông · Thả tim','/stickers/original/characters.png',1);
INSERT INTO stickers (id,pack_id,code,name,image_url,sort_order) VALUES ('aloute-dog-2','aloute-dog','atlas-18','Cún Bông · Cười vui','/stickers/original/characters.png',2);
INSERT INTO stickers (id,pack_id,code,name,image_url,sort_order) VALUES ('aloute-dog-3','aloute-dog','atlas-19','Cún Bông · Khóc nhè','/stickers/original/characters.png',3);
INSERT INTO stickers (id,pack_id,code,name,image_url,sort_order) VALUES ('aloute-dog-4','aloute-dog','atlas-20','Cún Bông · Giận dỗi','/stickers/original/characters.png',4);
INSERT INTO stickers (id,pack_id,code,name,image_url,sort_order) VALUES ('aloute-dog-5','aloute-dog','atlas-21','Cún Bông · Ngạc nhiên','/stickers/original/characters.png',5);
INSERT INTO stickers (id,pack_id,code,name,image_url,sort_order) VALUES ('aloute-dog-6','aloute-dog','atlas-22','Cún Bông · Buồn ngủ','/stickers/original/characters.png',6);
INSERT INTO stickers (id,pack_id,code,name,image_url,sort_order) VALUES ('aloute-dog-7','aloute-dog','atlas-23','Cún Bông · Cố lên','/stickers/original/characters.png',7);
