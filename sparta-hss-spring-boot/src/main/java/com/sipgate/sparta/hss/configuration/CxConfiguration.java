package com.sipgate.sparta.hss.configuration;

import com.sipgate.sparta.hss.diameter.client.DiameterConfig;
import com.sipgate.sparta.hss.diameter.client.DiameterSessions;
import com.sipgate.sparta.hss.diameter.common.auth.Authenticator;
import com.sipgate.sparta.hss.diameter.cx.mar.MultimediaAuthHandler;
import com.sipgate.sparta.hss.diameter.cx.rtr.RegistrationTerminationOutboundListener;
import com.sipgate.sparta.hss.diameter.cx.sar.ServerAssignmentHandler;
import com.sipgate.sparta.hss.diameter.cx.sar.service.ImsService;
import com.sipgate.sparta.hss.diameter.cx.sar.service.SimService;
import com.sipgate.sparta.hss.event.EventPublisher;
import com.sipgate.sparta.hss.persistence.ImsiDao;
import com.sipgate.sparta.hss.persistence.ImsiScscfDao;
import com.sipgate.sparta.hss.persistence.LocationIpSmGwDao;
import com.sipgate.sparta.hss.persistence.MsisdnDao;
import com.sipgate.sparta.hss.persistence.SimDao;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/// The Cx/Dx interface: request handlers, the IMS services and the outbound RTR side.
@AutoConfiguration
@ConditionalOnDiameterCapability(DiameterConfig.CAPABILITY_CX_DX)
public class CxConfiguration {

    @Bean
    public SimService simService(final MsisdnDao msisdnDao, final ImsiDao imsiDao) {
        return new SimService(msisdnDao, imsiDao);
    }

    @Bean
    public ImsService imsService(
        final ImsiScscfDao imsiScscfDao,
        final LocationIpSmGwDao locationIpSmGwDao,
        @Value("${sipgate.ims.userProfile.path}") final String userProfilePath) throws JAXBException, IOException {
        return new ImsService(imsiScscfDao, locationIpSmGwDao, userProfilePath);
    }

    @Bean
    public MultimediaAuthHandler multimediaAuthHandler(
        final SimDao simDao,
        final ImsiScscfDao imsiScscfDao,
        final Authenticator authenticator,
        final EventPublisher eventPublisher,
        final DiameterSessions diameterSessions) {
        return new MultimediaAuthHandler(simDao, imsiScscfDao, authenticator, eventPublisher, diameterSessions);
    }

    @Bean
    public ServerAssignmentHandler serverAssignmentHandler(
        final SimService simService,
        final ImsService imsService,
        final MeterRegistry meterRegistry,
        final EventPublisher eventPublisher) {
        return new ServerAssignmentHandler(simService, imsService, meterRegistry, eventPublisher);
    }

    @Bean
    public RegistrationTerminationOutboundListener registrationTerminationOutboundListener(final DiameterSessions diameterSessions) {
        return new RegistrationTerminationOutboundListener(diameterSessions);
    }

    @Bean
    public CxOutboundEventDispatcher cxOutboundEventDispatcher(
        final RegistrationTerminationOutboundListener registrationTerminationOutboundListener) {
        return new CxOutboundEventDispatcher(registrationTerminationOutboundListener);
    }
}
