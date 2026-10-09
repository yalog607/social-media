package com.aloute.dto.wallet;

import com.aloute.model.wallet.FanBadge;

import java.util.UUID;

/** Một fan của Creator: tổng Xu đã tặng và huy hiệu tương ứng (null nếu chưa đạt ngưỡng nào). */
public record FanView(UUID userId, String username, String displayName, long totalDonated, FanBadge badge) {
}
