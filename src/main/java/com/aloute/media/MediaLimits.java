package com.aloute.media;

/** Giới hạn cho ảnh/video đính kèm bài đăng. Con số khớp với spec Giai đoạn 2. */
public final class MediaLimits {

    /** Số ảnh tối đa mỗi bài. */
    public static final int MAX_IMAGES = 4;
    /** Dung lượng tối đa của MỘT ảnh gửi lên (trước khi thu nhỏ). */
    public static final long MAX_IMAGE_BYTES = 8L * 1024 * 1024;
    /** Dung lượng tối đa của video (chọn theo dung lượng đĩa VPS). */
    public static final long MAX_VIDEO_BYTES = 25L * 1024 * 1024;
    /** Số điểm ảnh tối đa khi giải mã, chống decompression bomb. */
    public static final long MAX_PIXELS = 40_000_000L;
    /** Cạnh dài nhất của ảnh sau khi lưu. */
    public static final int MAX_DIMENSION = 1920;

    private MediaLimits() {
    }
}
