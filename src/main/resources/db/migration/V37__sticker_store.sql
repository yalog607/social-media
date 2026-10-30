-- ALOUTE V37: Cửa hàng nhãn dán (Sticker Store) và gửi nhãn dán trong chat

CREATE TABLE sticker_packs (
    id             VARCHAR(50) PRIMARY KEY,
    name           VARCHAR(100) NOT NULL,
    description    VARCHAR(255),
    icon_url       VARCHAR(500) NOT NULL,
    author         VARCHAR(100) NOT NULL DEFAULT 'ALOUTE',
    total_stickers INT NOT NULL DEFAULT 0,
    is_default     BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order     INT NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE stickers (
    id         VARCHAR(80) PRIMARY KEY,
    pack_id    VARCHAR(50) NOT NULL REFERENCES sticker_packs (id) ON DELETE CASCADE,
    code       VARCHAR(50) NOT NULL,
    name       VARCHAR(100) NOT NULL,
    image_url  VARCHAR(500) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0
);
CREATE INDEX ix_stickers_pack ON stickers (pack_id, sort_order);

CREATE TABLE user_sticker_packs (
    id           UUID PRIMARY KEY,
    user_id      UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    pack_id      VARCHAR(50) NOT NULL REFERENCES sticker_packs (id) ON DELETE CASCADE,
    installed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_sticker_packs UNIQUE (user_id, pack_id)
);
CREATE INDEX ix_user_sticker_packs_user ON user_sticker_packs (user_id);

ALTER TABLE messages ADD COLUMN sticker_id VARCHAR(80) REFERENCES stickers (id);

-- Dữ liệu mẫu 5 gói nhãn dán
INSERT INTO sticker_packs (id, name, description, icon_url, author, total_stickers, is_default, sort_order) VALUES
('meo-cute', 'Mèo Con Nhí Nhảnh', 'Bộ nhãn dán mèo con siêu tinh nghịch, dễ thương', '/stickers/meo-cute/icon.svg', 'ALOUTE Studio', 8, true, 1),
('lop-hoc-vui', 'Lớp Học Tinh Nghịch', 'Những khoảnh khắc lầy lội, hài hước tuổi học trò', '/stickers/lop-hoc-vui/icon.svg', 'ALOUTE Studio', 8, true, 2),
('gau-hoc-gioi', 'Gấu Chăm Học', 'Đồng hành cùng bạn vượt qua mọi kỳ thi và deadline', '/stickers/gau-hoc-gioi/icon.svg', 'ALOUTE Studio', 6, false, 3),
('ech-huong-noi', 'Ếch Hướng Nội', 'Dành riêng cho những tâm hồn thích ở một mình', '/stickers/ech-huong-noi/icon.svg', 'ALOUTE Studio', 6, false, 4),
('genz-meme', 'Hệ Tư Tưởng Gen Z', 'Cười thả ga với các câu nói trendy nhất mạng xã hội', '/stickers/genz-meme/icon.svg', 'ALOUTE Studio', 6, false, 5);

-- Nhãn dán cho gói Mèo Con Nhí Nhảnh (meo-cute)
INSERT INTO stickers (id, pack_id, code, name, image_url, sort_order) VALUES
('meo_chao', 'meo-cute', 'meo_chao', 'Mèo chào bạn', '/stickers/meo-cute/meo_chao.svg', 1),
('meo_tim', 'meo-cute', 'meo_tim', 'Bắn tim nè', '/stickers/meo-cute/meo_tim.svg', 2),
('meo_cuoi', 'meo-cute', 'meo_cuoi', 'Cười hí hí', '/stickers/meo-cute/meo_cuoi.svg', 3),
('meo_khoc', 'meo-cute', 'meo_khoc', 'Khóc hu hu', '/stickers/meo-cute/meo_khoc.svg', 4),
('meo_doi', 'meo-cute', 'meo_doi', 'Dỗi rồi á', '/stickers/meo-cute/meo_doi.svg', 5),
('meo_ngo', 'meo-cute', 'meo_ngo', 'Ngơ ngác', '/stickers/meo-cute/meo_ngo.svg', 6),
('meo_ngu', 'meo-cute', 'meo_ngu', 'Ngủ khò khò', '/stickers/meo-cute/meo_ngu.svg', 7),
('meo_hoc', 'meo-cute', 'meo_hoc', 'Chăm học bài', '/stickers/meo-cute/meo_hoc.svg', 8);

-- Nhãn dán cho gói Lớp Học Tinh Nghịch (lop-hoc-vui)
INSERT INTO stickers (id, pack_id, code, name, image_url, sort_order) VALUES
('lop_co_giao', 'lop-hoc-vui', 'lop_co_giao', 'Cô giáo đến!', '/stickers/lop-hoc-vui/lop_co_giao.svg', 1),
('lop_kt_15p', 'lop-hoc-vui', 'lop_kt_15p', 'Kiểm tra 15p', '/stickers/lop-hoc-vui/lop_kt_15p.svg', 2),
('lop_chep_bai', 'lop-hoc-vui', 'lop_chep_bai', 'Chép bài với', '/stickers/lop-hoc-vui/lop_chep_bai.svg', 3),
('lop_can_tin', 'lop-hoc-vui', 'lop_can_tin', 'Đi căn tin ê', '/stickers/lop-hoc-vui/lop_can_tin.svg', 4),
('lop_het_tien', 'lop-hoc-vui', 'lop_het_tien', 'Hết tiền rồi', '/stickers/lop-hoc-vui/lop_het_tien.svg', 5),
('lop_tan_hoc', 'lop-hoc-vui', 'lop_tan_hoc', 'Về thôi!', '/stickers/lop-hoc-vui/lop_tan_hoc.svg', 6),
('lop_10_diem', 'lop-hoc-vui', 'lop_10_diem', 'Mười điểm', '/stickers/lop-hoc-vui/lop_10_diem.svg', 7),
('lop_xiu', 'lop-hoc-vui', 'lop_xiu', 'Xỉu up xỉu down', '/stickers/lop-hoc-vui/lop_xiu.svg', 8);

-- Nhãn dán cho gói Gấu Chăm Học (gau-hoc-gioi)
INSERT INTO stickers (id, pack_id, code, name, image_url, sort_order) VALUES
('gau_on_thi', 'gau-hoc-gioi', 'gau_on_thi', 'Đang ôn thi', '/stickers/gau-hoc-gioi/gau_on_thi.svg', 1),
('gau_cafe', 'gau-hoc-gioi', 'gau_cafe', 'Cà phê thức đêm', '/stickers/gau-hoc-gioi/gau_cafe.svg', 2),
('gau_deadline', 'gau-hoc-gioi', 'gau_deadline', 'Deadline dí', '/stickers/gau-hoc-gioi/gau_deadline.svg', 3),
('gau_co_len', 'gau-hoc-gioi', 'gau_co_len', 'Cố lên bạn!', '/stickers/gau-hoc-gioi/gau_co_len.svg', 4),
('gau_phao_hoa', 'gau-hoc-gioi', 'gau_phao_hoa', 'Đỗ rồi yeah', '/stickers/gau-hoc-gioi/gau_phao_hoa.svg', 5),
('gau_cam_on', 'gau-hoc-gioi', 'gau_cam_on', 'Cảm ơn nhiều', '/stickers/gau-hoc-gioi/gau_cam_on.svg', 6);

-- Nhãn dán cho gói Ếch Hướng Nội (ech-huong-noi)
INSERT INTO stickers (id, pack_id, code, name, image_url, sort_order) VALUES
('ech_tron', 'ech-huong-noi', 'ech_tron', 'Trốn góc này', '/stickers/ech-huong-noi/ech_tron.svg', 1),
('ech_ngai', 'ech-huong-noi', 'ech_ngai', 'Ngại quá à', '/stickers/ech-huong-noi/ech_ngai.svg', 2),
('ech_like', 'ech-huong-noi', 'ech_like', 'Thả like im lặng', '/stickers/ech-huong-noi/ech_like.svg', 3),
('ech_chao', 'ech-huong-noi', 'ech_chao', 'Chào từ xa', '/stickers/ech-huong-noi/ech_chao.svg', 4),
('ech_drama', 'ech-huong-noi', 'ech_drama', 'Ngồi hóng hớt', '/stickers/ech-huong-noi/ech_drama.svg', 5),
('ech_overthinking', 'ech-huong-noi', 'ech_overthinking', 'Overthinking', '/stickers/ech-huong-noi/ech_overthinking.svg', 6);

-- Nhãn dán cho gói Hệ Tư Tưởng Gen Z (genz-meme)
INSERT INTO stickers (id, pack_id, code, name, image_url, sort_order) VALUES
('gz_alo', 'genz-meme', 'gz_alo', 'Ủa alo?', '/stickers/genz-meme/gz_alo.svg', 1),
('gz_cuu', 'genz-meme', 'gz_cuu', 'Cứu tôi với', '/stickers/genz-meme/gz_cuu.svg', 2),
('gz_dinh', 'genz-meme', 'gz_dinh', 'Đỉnh nóc', '/stickers/genz-meme/gz_dinh.svg', 3),
('gz_oke', 'genz-meme', 'gz_oke', 'Rất là oke', '/stickers/genz-meme/gz_oke.svg', 4),
('gz_cham_kam', 'genz-meme', 'gz_cham_kam', 'Chầm kảm luôn', '/stickers/genz-meme/gz_cham_kam.svg', 5),
('gz_10_diem', 'genz-meme', 'gz_10_diem', 'Không có nhưng', '/stickers/genz-meme/gz_10_diem.svg', 6);

