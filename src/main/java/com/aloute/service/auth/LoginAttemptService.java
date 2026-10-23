package com.aloute.service.auth;

import com.aloute.config.AlouteProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Giới hạn số lần đăng nhập sai (bộ nhớ trong tiến trình).
 * Chặn theo cặp IP+định danh và theo riêng IP (ngưỡng cao hơn) để chống dò mật khẩu và dò tài khoản.
 */
@Component
public class LoginAttemptService {

    private static final int IP_MULTIPLIER = 4;

    private final int maxAttempts;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public LoginAttemptService(AlouteProperties props, Clock clock) {
        this.maxAttempts = props.security().loginMaxAttempts();
        this.window = Duration.ofMinutes(props.security().loginWindowMinutes());
        this.clock = clock;
    }

    public boolean isBlocked(String ip, String identifier) {
        return count(pairKey(ip, identifier)) >= maxAttempts
                || count(ipKey(ip)) >= maxAttempts * IP_MULTIPLIER;
    }

    public void recordFailure(String ip, String identifier) {
        add(pairKey(ip, identifier));
        add(ipKey(ip));
    }

    public void reset(String ip, String identifier) {
        failures.remove(pairKey(ip, identifier));
    }

    public Duration window() {
        return window;
    }

    private static String pairKey(String ip, String identifier) {
        return "p|" + ip + "|" + identifier.trim().toLowerCase(Locale.ROOT);
    }

    private static String ipKey(String ip) {
        return "i|" + ip;
    }

    private int count(String key) {
        Deque<Instant> deque = failures.get(key);
        if (deque == null) {
            return 0;
        }
        synchronized (deque) {
            prune(deque);
            return deque.size();
        }
    }

    private void add(String key) {
        Deque<Instant> deque = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            prune(deque);
            deque.addLast(clock.instant());
        }
    }

    private void prune(Deque<Instant> deque) {
        Instant cutoff = clock.instant().minus(window);
        while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
            deque.pollFirst();
        }
    }
}
