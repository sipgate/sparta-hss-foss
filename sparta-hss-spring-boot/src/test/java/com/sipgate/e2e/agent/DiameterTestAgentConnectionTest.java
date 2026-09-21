package com.sipgate.e2e.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.sipgate.sparta.diameter._3gpp.common._3gppConstants;
import com.sipgate.sparta.diameter._3gpp.s6a.S6aConstants;
import com.sipgate.sparta.diameter.base.session.DiameterInitiatorSession;
import com.sipgate.sparta.diameter.base.session.DiameterNodeConfig;
import com.sipgate.sparta.diameter.base.session.PeerState;
import com.sipgate.sparta.diameter.base.transport.DiameterNode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DiameterTestAgentConnectionTest {

    private static final DiameterNodeConfig CLIENT_CONFIG = new DiameterNodeConfig(
        "hss.e2e.epc.mnc001.mcc001.3gppnetwork.org",
        "epc.mnc001.mcc001.3gppnetwork.org",
        List.of(InetAddress.getLoopbackAddress()),
        0,
        "sparta-hss-test-client",
        new DiameterNodeConfig.Capabilities(
            List.of(),
            List.of(),
            List.of((long) _3gppConstants.VENDOR_ID_3GPP),
            List.of(new DiameterNodeConfig.VendorSpecificApp(
                _3gppConstants.VENDOR_ID_3GPP, S6aConstants.APP_ID_S6A_S6D))),
        Duration.ofSeconds(6),
        Duration.ofSeconds(1));

    @Test
    void hss_side_client_connects_and_agent_reaches_r_open() {
        final var agent = new DiameterTestAgent(
            "dra.e2e.epc.mnc001.mcc001.3gppnetwork.org",
            "epc.mnc001.mcc001.3gppnetwork.org").start(0);
        try (final var client = new DiameterNode(new SimpleMeterRegistry())) {
            final var clientSessionRef = new AtomicReference<DiameterInitiatorSession>();
            client.connect("localhost", agent.port(), reconnect -> {
                final var session = new DiameterInitiatorSession(
                    CLIENT_CONFIG, reconnect, new SimpleMeterRegistry());
                clientSessionRef.set(session);
                return session;
            });

            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                assertThat(clientSessionRef.get()).isNotNull();
                assertThat(clientSessionRef.get().getPeerState()).isEqualTo(PeerState.I_OPEN);
                assertThat(agent.peerState()).isEqualTo(PeerState.R_OPEN);
            });

            clientSessionRef.get().stop();
        } finally {
            agent.close();
        }
    }
}
