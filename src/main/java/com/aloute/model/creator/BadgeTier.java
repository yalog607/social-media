package com.aloute.model.creator;

import com.aloute.model.user.Role;

import java.time.LocalDate;
import java.time.Period;

/**
 * Tích theo số follower, chỉ dành cho Creator đủ 18 tuổi. Tính khi render (không lưu DB) nên tự hạ hoặc
 * mất ngay khi follower giảm, ngày sinh đổi, hoặc không còn là Creator chính (vai trò cao nhất).
 */
public enum BadgeTier {
    NONE, GOLD, PINK, RED;

    private static final long GOLD_MIN = 1_000;
    private static final long PINK_MIN = 100_000;
    private static final long RED_MIN = 1_000_000;
    private static final int MIN_AGE = 18;

    public static BadgeTier of(long followerCount, LocalDate birthDate, LocalDate today, Role primaryRole) {
        if (primaryRole != Role.CREATOR || birthDate == null || Period.between(birthDate, today).getYears() < MIN_AGE) {
            return NONE;
        }
        if (followerCount >= RED_MIN) {
            return RED;
        }
        if (followerCount >= PINK_MIN) {
            return PINK;
        }
        if (followerCount >= GOLD_MIN) {
            return GOLD;
        }
        return NONE;
    }
}
