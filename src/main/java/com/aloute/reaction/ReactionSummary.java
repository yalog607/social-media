package com.aloute.reaction;

import java.util.EnumMap;
import java.util.Map;

/** Số lượng từng loại cảm xúc của một bài, và loại mà người xem hiện tại (nếu có) đã thả. */
public record ReactionSummary(Map<ReactionType, Long> counts, ReactionType mine) {

    public static final ReactionSummary EMPTY = new ReactionSummary(Map.of(), null);

    public long total() {
        return counts.values().stream().mapToLong(Long::longValue).sum();
    }

    public long count(ReactionType type) {
        return counts.getOrDefault(type, 0L);
    }

    public static Map<ReactionType, Long> emptyCounts() {
        return new EnumMap<>(ReactionType.class);
    }
}
