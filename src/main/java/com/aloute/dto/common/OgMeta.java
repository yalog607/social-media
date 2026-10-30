package com.aloute.dto.common;

/**
 * Thẻ Open Graph của một trang. {@code image} và {@code url} luôn là địa chỉ tuyệt đối để bot mạng xã hội tải được;
 * {@code defaultImage} cho biết ảnh là ảnh OG mặc định của web (đã biết kích thước 1200x630).
 */
public record OgMeta(String title, String description, String image, String url, String type, boolean defaultImage) {
}
