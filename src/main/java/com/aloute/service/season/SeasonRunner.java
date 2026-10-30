package com.aloute.service.season;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "aloute.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SeasonRunner {
    private final SeasonService seasons;
    public SeasonRunner(SeasonService seasons) { this.seasons = seasons; }
    @Scheduled(initialDelay = 1000, fixedDelay = 60000)
    public void run() { seasons.advance(); }
}
