package com.aloute.wallet;

import java.util.UUID;

/** Một fan của Creator: tổng Xu đã tặng và huy hiệu tương ứng (null nếu chưa đạt ngưỡng nào). */
public record FanView(UUID userId, String username, String displayName, long totalDonated, FanBadge badge) {
}
