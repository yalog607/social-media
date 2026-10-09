package com.aloute.service.common;

import com.aloute.exception.common.RateLimitExceededException;
import com.aloute.model.common.RateAction;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Giới hạn tần suất theo cửa sổ trượt cho từng cặp (hành động, người dùng), lưu trong bộ nhớ tiến trình
 * (giống {@code LoginAttemptService}). Lần bị từ chối không được tính vào cửa sổ nên spam không kéo dài thời gian khóa.
 */
@Component
public class RateLimiter {

    /** Quá số khóa này thì dọn những người đã im lặng lâu hơn cửa sổ. */
    static final int DEFAULT_SWEEP_THRESHOLD = 10_000;

    private final Clock clock;
    private final int sweepThreshold;
    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    @Autowired
    public RateLimiter(Clock clock) {
        this(clock, DEFAULT_SWEEP_THRESHOLD);
    }

    RateLimiter(Clock clock, int sweepThreshold) {
        this.clock = clock;
        this.sweepThreshold = sweepThreshold;
    }

    /** @throws RateLimitExceededException nếu người dùng đã dùng hết lượt trong cửa sổ hiện tại */
    public void check(RateAction action, UUID userId) {
        Instant now = clock.instant();
        if (hits.size() > sweepThreshold) {
            sweep(now);
        }
        Deque<Instant> deque = hits.computeIfAbsent(action.name() + ":" + userId, key -> new ArrayDeque<>());
        synchronized (deque) {
            prune(deque, now.minus(action.window()));
            if (deque.size() >= action.max()) {
                throw new RateLimitExceededException(action.message());
            }
            deque.addLast(now);
        }
    }

    int trackedKeys() {
        return hits.size();
    }

    private void sweep(Instant now) {
        hits.entrySet().removeIf(entry -> {
            RateAction action = RateAction.valueOf(entry.getKey().substring(0, entry.getKey().indexOf(':')));
            Deque<Instant> deque = entry.getValue();
            synchronized (deque) {
                prune(deque, now.minus(action.window()));
                return deque.isEmpty();
            }
        });
    }

    private static void prune(Deque<Instant> deque, Instant cutoff) {
        while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
            deque.pollFirst();
        }
    }
}
