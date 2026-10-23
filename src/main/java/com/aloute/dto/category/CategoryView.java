package com.aloute.dto.category;

import java.util.UUID;

/** Một danh mục bài viết. {@code active} false nghĩa là không còn chọn được cho bài mới (bài cũ vẫn giữ nhãn). */
public record CategoryView(UUID id, String name, String slug, boolean active) {
}
