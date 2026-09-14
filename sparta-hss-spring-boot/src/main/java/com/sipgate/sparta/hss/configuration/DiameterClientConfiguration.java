package com.sipgate.sparta.hss.configuration;

import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.VENDOR_ID_3GPP;

import com.sipgate.sparta.diameter.base.session.DiameterNodeConfig;
import com.sipgate.sparta.hss.diameter.client.DiameterConfig;
import com.sipgate.sparta.hss.diameter.client.DiameterConnectionHandler;
import com.sipgate.sparta.hss.diameter.client.DiameterPeer;
import com.sipgate.sparta.hss.diameter.client.DiameterSessions;
import com.sipgate.sparta.hss.diameter.client.RegisterableDiameterHandler;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.AutoConfiguration;

/// Builds the Diameter client from the `sipgate.diameter` properties and connects it to the
/// configured peers on startup. The advertised applications (Cx/Dx, S6a/S6d, SWx) follow the
/// configured capabilities, so the active interfaces are a deployment decision.
@AutoConfiguration
@EnableConfigurationProperties(DiameterConfig.class)
public class DiameterClientConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(DiameterClientConfiguration.class);
    private static final long NO_VENDOR_ID = 0L;
    private static final String PRODUCT_NAME = "sparta-hss";

    @Bean
    public DiameterNodeConfig diameterNodeConfig(final DiameterConfig config) throws UnknownHostException {
        final var advertisedApps = config.capabilitiesAsLongs();
        final var vendorSpecificApps = advertisedApps.stream()
            .map(appId -> new DiameterNodeConfig.VendorSpecificApp(VENDOR_ID_3GPP, appId))
            .toList();
        final var capabilities = new DiameterNodeConfig.Capabilities(
            advertisedApps,
            List.of(),
            List.of((long) VENDOR_ID_3GPP),
            vendorSpecificApps);

        final InetAddress hostAddress;
        if (config.hostIp() != null) {
            hostAddress = InetAddress.getByName(config.hostIp());
        } else {
            hostAddress = InetAddress.getLocalHost();
            LOGGER.info("No host-ip configured, using local host address: {}", hostAddress.getHostAddress());
        }

        return new DiameterNodeConfig(
            config.originHost(),
            config.originRealm(),
            List.of(hostAddress),
            NO_VENDOR_ID,
            PRODUCT_NAME,
            capabilities,
            config.watchdogInterval(),
            config.reconnectDelay());
    }

    @Bean
    public DiameterSessions diameterSessions(final DiameterNodeConfig diameterNodeConfig) {
        return new DiameterSessions(diameterNodeConfig);
    }

    @Bean(initMethod = "connect", destroyMethod = "close")
    public DiameterConnectionHandler diameterConnectionHandler(
        final DiameterNodeConfig diameterNodeConfig,
        final DiameterConfig config,
        final MeterRegistry meterRegistry,
        final DiameterSessions diameterSessions,
        @Qualifier("inboundThreadPoolExecutor") final Executor inboundExecutor,
        final ObjectProvider<RegisterableDiameterHandler<?, ?>> handlers) {
        final var peers = config.peers().stream()
            .map(peer -> new DiameterPeer(peer.host(), peer.port()))
            .toList();
        return new DiameterConnectionHandler(
            diameterNodeConfig,
            peers,
            meterRegistry,
            diameterSessions,
            inboundExecutor,
            handlers.stream().toList());
    }
}
