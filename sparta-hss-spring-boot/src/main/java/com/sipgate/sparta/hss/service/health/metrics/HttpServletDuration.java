package com.sipgate.sparta.hss.service.health.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpServletDuration {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpServletDuration.class);

    // probably needs fine-tuning
    private static final Duration[] SLOS = {
            Duration.ofMillis(1),
            Duration.ofMillis(2),
            Duration.ofMillis(4),
            Duration.ofMillis(8),
            Duration.ofMillis(16),
            Duration.ofMillis(32),
            Duration.ofMillis(64),
            Duration.ofMillis(128),
            Duration.ofMillis(256),
            Duration.ofMillis(512),
            Duration.ofMillis(1024),
            Duration.ofMillis(2048),
            Duration.ofMillis(4096),
            Duration.ofMillis(8192),
    };

    private HttpServletDuration() {
    }

    public static <T> T record(final MeterRegistry meterRegistry, final String method, final String path, final Supplier<T> supplier) {
        if (method == null || path == null) {
            LOGGER.error("method '{}' or path '{}' is null", method, path);
            return supplier.get();
        }

        return Timer
                .builder("http.servlet.duration")
                .serviceLevelObjectives(SLOS)
                .tag("method", method)
                .tag("path", path)
                .register(meterRegistry)
                .record(supplier);
    }
}
