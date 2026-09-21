package com.sipgate.e2e.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.sipgate.sparta.diameter._3gpp.common._3gppConstants;
import com.sipgate.sparta.diameter._3gpp.s6a.S6aConstants;
import com.sipgate.sparta.diameter._3gpp.s6a.messages.CancelLocationRequest;
import com.sipgate.sparta.diameter._3gpp.s6a.messages.UpdateLocationRequest;
import com.sipgate.sparta.diameter.base.core.DiameterConstants;
import com.sipgate.sparta.diameter.base.core.DiameterMessageFactory;
import com.sipgate.sparta.diameter.base.session.DiameterInitiatorSession;
import com.sipgate.sparta.diameter.base.session.DiameterNodeConfig;
import com.sipgate.sparta.diameter.base.transport.DiameterNode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DiameterTestAgentExchangeTest {

    private static final String HSS = "hss.e2e.epc.mnc001.mcc001.3gppnetwork.org";
    private static final String REALM = "epc.mnc001.mcc001.3gppnetwork.org";
    private static final String AGENT = "dra.e2e.epc.mnc001.mcc001.3gppnetwork.org";

    private static final DiameterNodeConfig HSS_CONFIG = new DiameterNodeConfig(
        HSS, REALM, List.of(InetAddress.getLoopbackAddress()), 0, "sparta-hss-test-client",
        new DiameterNodeConfig.Capabilities(
            List.of(), List.of(), List.of((long) _3gppConstants.VENDOR_ID_3GPP),
            List.of(new DiameterNodeConfig.VendorSpecificApp(
                _3gppConstants.VENDOR_ID_3GPP, S6aConstants.APP_ID_S6A_S6D))),
        Duration.ofSeconds(6), Duration.ofSeconds(1));

    /** The "HSS" side answers an inbound ULR with SUCCESS, and can send a CLR to the agent. */
    @Test
    void request_gets_answer_and_outbound_request_is_captured() throws Exception {
        final var agent = new DiameterTestAgent(AGENT, REALM).start(0);
        try (final var hssNode = new DiameterNode(new SimpleMeterRegistry())) {

            final var hssSessionRef = new AtomicReference<DiameterInitiatorSession>();
            hssNode.connect("localhost", agent.port(), reconnect -> {
                final var session = new DiameterInitiatorSession(HSS_CONFIG, reconnect, new SimpleMeterRegistry());
                session.setHandler(UpdateLocationRequest.In.class, req ->
                    CompletableFuture.completedFuture(
                        DiameterMessageFactory.createAnswer(req, DiameterConstants.RES_DIAMETER_SUCCESS)));
                hssSessionRef.set(session);
                return session;
            });
            agent.awaitConnected(Duration.ofSeconds(10));

            // Direction 1: agent -> HSS request, answer comes back
            final var ulr = new UpdateLocationRequest.Out();
            ulr.setSessionId(AGENT + ";1;1");
            ulr.setOriginHost(AGENT);
            ulr.setOriginRealm(REALM);
            ulr.setDestinationHost(HSS);
            ulr.setDestinationRealm(REALM);
            ulr.setUserName("001010000000001");
            final var ula = agent.sendAndWait(ulr, Duration.ofSeconds(5));
            assertThat(ula.getResultCode()).isEqualTo(DiameterConstants.RES_DIAMETER_SUCCESS);

            // Direction 2: HSS -> agent request, captured + auto-answered
            final var clr = new CancelLocationRequest.Out();
            clr.setSessionId(HSS + ";1;1");
            clr.setOriginHost(HSS);
            clr.setOriginRealm(REALM);
            clr.setDestinationHost(AGENT);
            clr.setDestinationRealm(REALM);
            clr.setUserName("001010000000001");
            final var clrAnswer = hssSessionRef.get().send(clr);

            final var captured = agent.awaitRequest(CancelLocationRequest.In.class, Duration.ofSeconds(5));
            assertThat(captured.getUserName()).isEqualTo("001010000000001");
            await().atMost(Duration.ofSeconds(5)).until(clrAnswer::isDone);
            assertThat(clrAnswer.get().getResultCode()).isEqualTo(DiameterConstants.RES_DIAMETER_SUCCESS);
        } finally {
            agent.close();
        }
    }
}
