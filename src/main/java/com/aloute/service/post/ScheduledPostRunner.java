package com.aloute.service.post;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Kích hoạt {@link ScheduledPostPublisher} mỗi phút. Tắt bằng {@code aloute.scheduling.enabled=false} (môi trường test). */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "aloute.scheduling.enabled", havingValue = "true", matchIfMissing = true)
class ScheduledPostRunner {

    private final ScheduledPostPublisher publisher;

    ScheduledPostRunner(ScheduledPostPublisher publisher) {
        this.publisher = publisher;
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
    void run() {
        publisher.publishDue();
    }
}
