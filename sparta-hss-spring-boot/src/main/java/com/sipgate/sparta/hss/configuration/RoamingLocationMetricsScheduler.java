package com.sipgate.sparta.hss.configuration;

import com.sipgate.sparta.hss.service.health.metrics.RoamingLocationMetrics;
import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.annotation.Scheduled;

/// Refreshes the roaming-location gauge on the schedule the core documents but does not own.
public class RoamingLocationMetricsScheduler {

    private final RoamingLocationMetrics roamingLocationMetrics;

    public RoamingLocationMetricsScheduler(final RoamingLocationMetrics roamingLocationMetrics) {
        this.roamingLocationMetrics = roamingLocationMetrics;
    }

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.MINUTES)
    public void update() {
        roamingLocationMetrics.updateRoamingLocations();
    }
}
