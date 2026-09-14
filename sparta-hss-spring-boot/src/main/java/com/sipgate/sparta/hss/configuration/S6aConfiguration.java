package com.sipgate.sparta.hss.configuration;

import com.sipgate.sparta.hss.diameter.client.DiameterConfig;
import com.sipgate.sparta.hss.diameter.client.DiameterSessions;
import com.sipgate.sparta.hss.diameter.common.auth.Authenticator;
import com.sipgate.sparta.hss.diameter.s6a.air.AuthenticationInfoHandler;
import com.sipgate.sparta.hss.diameter.s6a.air.EutranAccessPolicy;
import com.sipgate.sparta.hss.diameter.s6a.clr.CancelLocationOutboundListener;
import com.sipgate.sparta.hss.diameter.s6a.common.SubscriptionDataEncoder;
import com.sipgate.sparta.hss.diameter.s6a.common.SubscriptionDataFactory;
import com.sipgate.sparta.hss.diameter.s6a.isd.InsertSubscriberDataOutboundListener;
import com.sipgate.sparta.hss.diameter.s6a.nir.NotifyHandler;
import com.sipgate.sparta.hss.diameter.s6a.pur.PurgeUeHandler;
import com.sipgate.sparta.hss.diameter.s6a.ulr.UpdateLocationHandler;
import com.sipgate.sparta.hss.event.EventPublisher;
import com.sipgate.sparta.hss.persistence.EffectiveProfileDao;
import com.sipgate.sparta.hss.persistence.ImsiProfileDao;
import com.sipgate.sparta.hss.persistence.LocationLteDao;
import com.sipgate.sparta.hss.persistence.SimDao;
import com.sipgate.sparta.hss.persistence.TacProfileDao;
import com.sipgate.sparta.hss.service.health.metrics.RoamingLocationMetrics;
import com.sipgate.sparta.hss.service.profile.BatchProfileService;
import com.sipgate.sparta.hss.service.profile.ProfileService;
import com.sipgate.sparta.hss.service.roaming.BlockRoamingService;
import com.sipgate.sparta.hss.service.roaming.EmergencyRoamingRestriction;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/// The S6a/S6d interface: request handlers plus the outbound CLR/ISD side.
@AutoConfiguration
@ConditionalOnDiameterCapability(DiameterConfig.CAPABILITY_S6A_S6D)
@EnableConfigurationProperties(MetricsProperties.class)
public class S6aConfiguration {

    @Bean
    public RoamingLocationMetrics roamingLocationMetrics(
        final MeterRegistry meterRegistry,
        final LocationLteDao locationLteDao,
        final MetricsProperties metricsProperties) {
        return new RoamingLocationMetrics(meterRegistry, locationLteDao, metricsProperties.esimImsiRanges());
    }

    @Bean
    public RoamingLocationMetricsScheduler roamingLocationMetricsScheduler(
        final RoamingLocationMetrics roamingLocationMetrics) {
        return new RoamingLocationMetricsScheduler(roamingLocationMetrics);
    }

    @Bean
    public AuthenticationInfoHandler authenticationInfoHandler(
        final SimDao simDao,
        final EutranAccessPolicy eutranAccessPolicy,
        final Authenticator authenticator) {
        return new AuthenticationInfoHandler(simDao, eutranAccessPolicy, authenticator);
    }

    @Bean
    public UpdateLocationHandler updateLocationHandler(
        final SimDao simDao,
        final LocationLteDao locationLteDao,
        final ImsiProfileDao imsiProfileDao,
        final TacProfileDao tacProfileDao,
        final SubscriptionDataFactory subscriptionDataFactory,
        final MeterRegistry meterRegistry,
        final EventPublisher eventPublisher,
        final BlockRoamingService blockRoamingService,
        final EmergencyRoamingRestriction emergencyRoamingRestriction,
        final DiameterSessions diameterSessions) {
        return new UpdateLocationHandler(
            simDao, locationLteDao, imsiProfileDao, tacProfileDao, subscriptionDataFactory,
            meterRegistry, eventPublisher, blockRoamingService, emergencyRoamingRestriction,
            diameterSessions);
    }

    @Bean
    public EmergencyRoamingRestriction emergencyRoamingRestriction(
        @Value("${sipgate.roaming.emergency-mcc-allowlist.enabled:false}") final boolean enabled,
        @Value("${sipgate.roaming.emergency-mcc-allowlist.allowed-mccs:}") final Set<String> allowedMccs) {
        final var effectiveAllowlist = allowedMccs.isEmpty()
            ? EmergencyRoamingRestriction.ZONE_ONE_MCCS
            : allowedMccs;
        return new EmergencyRoamingRestriction(enabled, effectiveAllowlist);
    }

    @Bean
    public PurgeUeHandler purgeUeHandler(final SimDao simDao, final LocationLteDao locationLteDao) {
        return new PurgeUeHandler(simDao, locationLteDao);
    }

    @Bean
    public NotifyHandler notifyHandler(final SimDao simDao) {
        return new NotifyHandler(simDao);
    }

    @Bean
    public CancelLocationOutboundListener cancelLocationOutboundListener(final DiameterSessions diameterSessions) {
        return new CancelLocationOutboundListener(diameterSessions);
    }

    @Bean
    public InsertSubscriberDataOutboundListener insertSubscriberDataOutboundListener(final DiameterSessions diameterSessions) {
        return new InsertSubscriberDataOutboundListener(diameterSessions);
    }

    @Bean
    public S6aOutboundEventDispatcher s6aOutboundEventDispatcher(
        final CancelLocationOutboundListener cancelLocationOutboundListener,
        final InsertSubscriberDataOutboundListener insertSubscriberDataOutboundListener) {
        return new S6aOutboundEventDispatcher(cancelLocationOutboundListener, insertSubscriberDataOutboundListener);
    }

    @Bean
    public SubscriptionDataFactory subscriptionDataFactory(
        @Value("${sipgate.profileDir}") final String profileDir,
        final MeterRegistry meterRegistry,
        final SubscriptionDataEncoder subscriptionDataEncoder) {
        final var factory = new SubscriptionDataFactory(profileDir, meterRegistry, subscriptionDataEncoder);
        factory.init();
        return factory;
    }

    @Bean
    public ProfileService profileService(
        final SubscriptionDataFactory subscriptionDataFactory,
        final EventPublisher eventPublisher,
        final DiameterSessions diameterSessions) {
        return new ProfileService(subscriptionDataFactory, eventPublisher, diameterSessions);
    }

    @Bean
    public BatchProfileService batchProfileService(
        final ImsiProfileDao imsiProfileDao,
        final TacProfileDao tacProfileDao,
        final ProfileService profileService,
        final EffectiveProfileDao effectiveProfileDao) {
        return new BatchProfileService(imsiProfileDao, tacProfileDao, profileService, effectiveProfileDao);
    }
}
