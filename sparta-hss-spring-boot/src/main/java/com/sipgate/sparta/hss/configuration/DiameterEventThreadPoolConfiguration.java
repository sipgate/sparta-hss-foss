package com.sipgate.sparta.hss.configuration;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/// Thread pools for Diameter request processing.
///
/// - `inboundThreadPoolExecutor` runs the inbound request handlers off the Netty event loop, which
///   also drives the DWR/DWA watchdog and every other request on a peer connection. Blocking work
///   (DB, event listeners, external authorization calls) must not run on the event loop.
/// - `outboundThreadPoolExecutor` runs the HSS-initiated outbound requests (RTR, CLR, ISD) via the
///   `@Async`-annotated outbound listeners, so the send never blocks the thread that published the event.
///
/// Both pools use the default `AbortPolicy`: when the bounded queue is full the task is rejected
/// rather than queued unboundedly, which the dispatch guard turns into an UNABLE_TO_COMPLY answer.
@AutoConfiguration
@EnableAsync
@EnableScheduling
public class DiameterEventThreadPoolConfiguration {

    @Bean(name = "inboundThreadPoolExecutor")
    public Executor inboundThreadPoolExecutor() {
        return makePool("diameter-inbound-");
    }

    @Bean(name = "outboundThreadPoolExecutor")
    public Executor outboundThreadPoolExecutor() {
        return makePool("diameter-outbound-");
    }

    private static Executor makePool(final String threadNamePrefix) {
        final var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(40);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix(threadNamePrefix);
        return executor;
    }
}
