package com.aloute.social;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link UUID#compareTo} so sánh CÓ DẤU trên từng nửa 64-bit, khác với so sánh 128-bit KHÔNG DẤU mà ràng buộc
 * {@code CHECK (user_a_id < user_b_id)} của Postgres dùng — hai cách này xếp thứ tự khác nhau với một số cặp
 * UUID cụ thể. {@code a} dưới đây có nửa đầu bắt đầu bằng {@code 0x80...} (bit cao nhất bật) nên
 * {@code UUID#compareTo} coi nó ÂM (nhỏ hơn {@code b}), trong khi so sánh không dấu (đúng với Postgres) thì
 * {@code a} lớn hơn {@code b} vì {@code 0x80 > 0x10}.
 */
class FriendServicePairTest {

    private static final UUID A = UUID.fromString("80000000-0000-4000-8000-000000000001");
    private static final UUID B = UUID.fromString("10000000-0000-4000-8000-000000000002");

    @Test
    void uuidCompareToDisagreesWithUnsignedByteOrder() {
        assertThat(A.compareTo(B)).as("minh chứng UUID#compareTo không dùng được để khớp CHECK của Postgres").isNegative();
        assertThat(A.toString().compareTo(B.toString())).isPositive();
    }

    @Test
    void pairAlwaysPutsTheLexicographicallySmallerUuidFirst() {
        assertThat(FriendService.pair(A, B)).isEqualTo(new FriendService.Pair(B, A));
        assertThat(FriendService.pair(B, A)).isEqualTo(new FriendService.Pair(B, A));
    }
}
