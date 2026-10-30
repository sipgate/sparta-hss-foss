package com.sipgate.sparta.hss.configuration;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/// Thread pool for Diameter inbound request processing.
///
/// `inboundThreadPoolExecutor` runs the inbound request handlers off the Netty event loop, which
/// also drives the DWR/DWA watchdog and every other request on a peer connection. Blocking work
/// (DB, event listeners, external authorization calls) must not run on the event loop.
///
/// The outbound request listeners (RTR, CLR, ISD) deliberately have NO pool of their own: the
/// `Cx`/`S6a`/`SwxOutboundEventDispatcher`s dispatch synchronously on the publishing thread and
/// inside the publisher's transaction, so the teardown observes the publisher's uncommitted state
/// (e.g. `isActive=false` from `SubscriptionStateService.deactivate`), keeps kafka message order,
/// and cannot lose events to a bounded pool's `AbortPolicy`. The listeners guard themselves with
/// try/catch, so a failed teardown neither rolls back the publisher nor fails its request.
@AutoConfiguration
@EnableAsync
@EnableScheduling
public class DiameterEventThreadPoolConfiguration {

    @Bean(name = "inboundThreadPoolExecutor")
    public Executor inboundThreadPoolExecutor() {
        final var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(40);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("diameter-inbound-");
        return executor;
    }
}
