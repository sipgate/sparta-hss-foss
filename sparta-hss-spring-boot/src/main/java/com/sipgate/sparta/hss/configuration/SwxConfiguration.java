package com.sipgate.sparta.hss.configuration;

import com.sipgate.sparta.hss.diameter.client.DiameterConfig;
import com.sipgate.sparta.hss.diameter.common.auth.EapAkaPrimeKeyDerivation;
import com.sipgate.sparta.hss.diameter.common.auth.Authenticator;
import com.sipgate.sparta.hss.diameter.s6a.common.SubscriptionDataEncoder;
import com.sipgate.sparta.hss.diameter.swx.mar.SwxMultimediaAuthHandler;
import com.sipgate.sparta.hss.diameter.swx.sar.Non3gppUserDataEncoder;
import com.sipgate.sparta.hss.diameter.swx.sar.Non3gppUserDataFactory;
import com.sipgate.sparta.hss.diameter.swx.sar.SwxServerAssignmentHandler;
import com.sipgate.sparta.hss.event.EventPublisher;
import com.sipgate.sparta.hss.persistence.ImsiProfileDao;
import com.sipgate.sparta.hss.persistence.LocationVowifiDao;
import com.sipgate.sparta.hss.persistence.SimDao;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/// The SWx interface: request handlers and the Non-3GPP-User-Data encoding.
@AutoConfiguration
@ConditionalOnDiameterCapability(DiameterConfig.CAPABILITY_SWX)
public class SwxConfiguration {

    @Bean
    public Non3gppUserDataEncoder non3gppUserDataEncoder(final SubscriptionDataEncoder subscriptionDataEncoder) {
        return new Non3gppUserDataEncoder(subscriptionDataEncoder);
    }

    @Bean
    public Non3gppUserDataFactory non3gppUserDataFactory(
        @Value("${sipgate.profileDir}") final String profileDir,
        final MeterRegistry meterRegistry,
        final Non3gppUserDataEncoder non3gppUserDataEncoder) {
        final var factory = new Non3gppUserDataFactory(profileDir, meterRegistry, non3gppUserDataEncoder);
        factory.init();
        return factory;
    }

    @Bean
    public SwxMultimediaAuthHandler swxMultimediaAuthHandler(
        final SimDao simDao,
        final LocationVowifiDao locationVowifiDao,
        final Authenticator authenticator,
        final EapAkaPrimeKeyDerivation eapAkaPrimeKeyDerivation,
        final EventPublisher eventPublisher) {
        return new SwxMultimediaAuthHandler(
            simDao, locationVowifiDao, authenticator, eapAkaPrimeKeyDerivation, eventPublisher);
    }

    @Bean
    public SwxServerAssignmentHandler swxServerAssignmentHandler(
        final SimDao simDao,
        final LocationVowifiDao locationVowifiDao,
        final ImsiProfileDao imsiProfileDao,
        final Non3gppUserDataFactory non3gppUserDataFactory,
        final EventPublisher eventPublisher,
        final MeterRegistry meterRegistry) {
        return new SwxServerAssignmentHandler(
            simDao, locationVowifiDao, imsiProfileDao, non3gppUserDataFactory, eventPublisher, meterRegistry);
    }
}
