package com.aloute.dto.common;

import java.util.List;

public record PageResult<T>(
        List<T> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
    public static <T> PageResult<T> of(List<T> items, int page, int size, long totalItems) {
        int safeSize = Math.max(1, size);
        int pages = (int) Math.ceil((double) totalItems / safeSize);
        return new PageResult<>(items, page, safeSize, totalItems, pages);
    }

    public boolean hasPrevious() {
        return page > 0;
    }

    public boolean hasNext() {
        return page + 1 < totalPages;
    }
}
