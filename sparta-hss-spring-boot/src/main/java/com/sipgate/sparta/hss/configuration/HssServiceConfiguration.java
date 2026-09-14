package com.sipgate.sparta.hss.configuration;

import com.sipgate.sparta.hss.diameter.common.auth.Authenticator;
import com.sipgate.sparta.hss.diameter.common.auth.EapAkaPrimeKeyDerivation;
import com.sipgate.sparta.hss.diameter.common.auth.MilenageAuthenticator;
import com.sipgate.sparta.hss.diameter.common.auth.RandomGenerator;
import com.sipgate.sparta.hss.diameter.s6a.air.EutranAccessPolicy;
import com.sipgate.sparta.hss.diameter.s6a.common.SubscriptionDataEncoder;
import com.sipgate.sparta.hss.event.EventPublisher;
import com.sipgate.sparta.hss.persistence.BlockRoamingDao;
import com.sipgate.sparta.hss.persistence.LocationLteDao;
import com.sipgate.sparta.hss.persistence.MsisdnScscfDao;
import com.sipgate.sparta.hss.service.roaming.BlockRoamingService;
import com.sipgate.sparta.hss.service.volte.location.LocationService;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.scheduling.annotation.Scheduled;

/// Provides the base module's services and its extension points as beans.
///
/// The extension points ([EventPublisher], [EutranAccessPolicy], [Authenticator]) are
/// conditional: an application built on top of this module replaces them by defining its own bean.
@AutoConfiguration
@EnableConfigurationProperties(AucProperties.class)
public class HssServiceConfiguration {

    /// Dispatches the core's domain events as Spring application events, so listeners register
    /// with the plain Spring `@EventListener` mechanism.
    @Bean
    @ConditionalOnMissingBean
    public EventPublisher eventPublisher(final ApplicationEventPublisher applicationEventPublisher) {
        return applicationEventPublisher::publishEvent;
    }

    @Bean
    @ConditionalOnMissingBean
    public EutranAccessPolicy eutranAccessPolicy() {
        return EutranAccessPolicy.allowAll();
    }

    @Bean
    public RandomGenerator randomGenerator() {
        return new RandomGenerator();
    }

    @Bean
    @ConditionalOnMissingBean
    public Authenticator milenageAuthenticator(
        final RandomGenerator randomGenerator,
        final MeterRegistry meterRegistry,
        final AucProperties aucProperties) {
        return new MilenageAuthenticator(randomGenerator, meterRegistry, aucProperties.storedKeyFormat());
    }

    @Bean
    public EapAkaPrimeKeyDerivation eapAkaPrimeKeyDerivation() {
        return new EapAkaPrimeKeyDerivation();
    }

    @Bean
    public SubscriptionDataEncoder subscriptionDataEncoder() {
        return new SubscriptionDataEncoder();
    }

    @Bean
    public BlockRoamingService blockRoamingService(final BlockRoamingDao blockRoamingDao, final MeterRegistry meterRegistry) {
        return new BlockRoamingService(blockRoamingDao, meterRegistry);
    }

    /// The base service exposes blocked-event expiry as a plain method; the Spring deployment
    /// drives it on the same 5-minute cadence the metric contract expects.
    @Bean
    public BlockedEventExpiry blockedEventExpiry(final BlockRoamingService blockRoamingService) {
        return new BlockedEventExpiry(blockRoamingService);
    }

    @Bean
    public LocationService locationService(final MsisdnScscfDao msisdnScscfDao, final LocationLteDao locationLteDao) {
        return new LocationService(msisdnScscfDao, locationLteDao);
    }

    public static class BlockedEventExpiry {

        private final BlockRoamingService blockRoamingService;

        BlockedEventExpiry(final BlockRoamingService blockRoamingService) {
            this.blockRoamingService = blockRoamingService;
        }

        @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
        public void expireOldBlockedEvents() {
            blockRoamingService.expireOldBlockedEvents();
        }
    }
}
