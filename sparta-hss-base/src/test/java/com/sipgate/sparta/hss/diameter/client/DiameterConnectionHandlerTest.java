package com.sipgate.sparta.hss.diameter.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants;
import com.sipgate.sparta.diameter._3gpp.s6a.S6aConstants;
import com.sipgate.sparta.diameter._3gpp.s6a.messages.AuthenticationInformationRequest;
import com.sipgate.sparta.diameter._3gpp.s6a.messages.NotifyRequest;
import com.sipgate.sparta.diameter._3gpp.s6a.messages.PurgeUeAnswer;
import com.sipgate.sparta.diameter._3gpp.s6a.messages.PurgeUeRequest;
import com.sipgate.sparta.diameter._3gpp.s6a.messages.UpdateLocationRequest;
import com.sipgate.sparta.diameter._3gpp.swx.SwxConstants;
import com.sipgate.sparta.diameter.base.core.Command;
import com.sipgate.sparta.diameter.base.core.DiameterConstants;
import com.sipgate.sparta.diameter.base.core.DiameterMessageFactory;
import com.sipgate.sparta.diameter.base.core.EndToEndId;
import com.sipgate.sparta.diameter.base.core.HopByHopId;
import com.sipgate.sparta.diameter.base.session.DiameterInitiatorSession;
import com.sipgate.sparta.diameter.base.session.DiameterNodeConfig;
import com.sipgate.sparta.diameter.base.session.DiameterRequestHandler;
import com.sipgate.sparta.hss.diameter.common.Factory;
import com.sipgate.sparta.hss.diameter.cx.mar.MultimediaAuthHandler;
import com.sipgate.sparta.hss.diameter.cx.sar.ServerAssignmentHandler;
import com.sipgate.sparta.hss.diameter.s6a.air.AuthenticationInfoHandler;
import com.sipgate.sparta.hss.diameter.s6a.nir.NotifyHandler;
import com.sipgate.sparta.hss.diameter.s6a.pur.PurgeUeHandler;
import com.sipgate.sparta.hss.diameter.s6a.ulr.UpdateLocationHandler;
import com.sipgate.sparta.hss.diameter.swx.mar.SwxMultimediaAuthHandler;
import com.sipgate.sparta.hss.diameter.swx.sar.SwxServerAssignmentHandler;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/// Verifies the dispatch guard around the request handlers: the lib's
/// DiameterSession.dispatchInboundRequest only converts *exceptionally completed futures* into
/// UNABLE_TO_COMPLY (5012) answers — a synchronous throw would propagate to Netty's
/// exceptionCaught and close the whole peer connection, a null future/answer would vanish
/// answerless in the lib's send path.
@ExtendWith(MockitoExtension.class)
class DiameterConnectionHandlerTest {

    private final DiameterNodeConfig config = Factory.nodeConfig("hss.test.local", "test.local");
    private final List<DiameterPeer> peers = List.of(new DiameterPeer("localhost", 13868));

    @Spy
    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
    @Mock
    private DiameterSessions diameterSessions;
    @Mock
    private MultimediaAuthHandler multimediaAuthHandler;
    @Mock
    private ServerAssignmentHandler serverAssignmentHandler;
    @Mock
    private AuthenticationInfoHandler authenticationInfoHandler;
    @Mock
    private NotifyHandler notifyHandler;
    @Mock
    private PurgeUeHandler purgeUeHandler;
    @Mock
    private UpdateLocationHandler updateLocationHandler;
    @Mock
    private SwxMultimediaAuthHandler swxMultimediaAuthHandler;
    @Mock
    private SwxServerAssignmentHandler swxServerAssignmentHandler;

    @Test
    void itRejectsTwoHandlersForTheSameRequestType() {
        final var otherPurgeUeHandler = mock(PurgeUeHandler.class);
        when(purgeUeHandler.requestType()).thenReturn(PurgeUeRequest.In.class);
        when(otherPurgeUeHandler.requestType()).thenReturn(PurgeUeRequest.In.class);

        assertThatThrownBy(() -> new DiameterConnectionHandler(
                config, peers, meterRegistry, diameterSessions, Runnable::run,
                List.of(purgeUeHandler, otherPurgeUeHandler)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(PurgeUeRequest.In.class.getName());
    }

    @Test
    void itRegistersAllEightHandlers() {
        final var session = mock(DiameterInitiatorSession.class);

        connectionHandler().registerHandlers(session);


        verify(session).setHandler(eq(AuthenticationInformationRequest.In.class), any());
        verify(session).setHandler(eq(NotifyRequest.In.class), any());
        verify(session).setHandler(eq(PurgeUeRequest.In.class), any());
        verify(session).setHandler(eq(UpdateLocationRequest.In.class), any());
        verify(session).setHandler(eq(com.sipgate.sparta.diameter._3gpp.cxdx.messages.MultimediaAuthRequest.In.class), any());
        verify(session).setHandler(eq(com.sipgate.sparta.diameter._3gpp.cxdx.messages.ServerAssignmentRequest.In.class), any());
        // SWx (app-id 16777265) — distinct In classes, registered independently from Cx.
        verify(session).setHandler(eq(com.sipgate.sparta.diameter._3gpp.swx.messages.MultimediaAuthRequest.In.class), any());
        verify(session).setHandler(eq(com.sipgate.sparta.diameter._3gpp.swx.messages.ServerAssignmentRequest.In.class), any());
    }

    @Test
    void itTurnsASynchronousHandlerThrowIntoAFailedFuture() throws Exception {
        final var boom = new IllegalArgumentException("malformed AVP");
        when(purgeUeHandler.handle(any())).thenThrow(boom);

        // must not throw out of handle() — that would reach Netty's exceptionCaught and close the peer
        final var future = registeredPurgeUeHandler().handle(buildRequest());

        assertThat(future).isCompletedExceptionally();
        assertThatThrownBy(future::join)
                .isInstanceOf(CompletionException.class)
                .hasCause(boom);
    }

    @Test
    void itTurnsANullFutureIntoAFailedFuture() throws Exception {
        when(purgeUeHandler.handle(any())).thenReturn(null);

        final var future = registeredPurgeUeHandler().handle(buildRequest());

        assertThat(future).isCompletedExceptionally();
        assertThatThrownBy(future::join)
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("null future");
    }

    @Test
    void itTurnsAFutureOfNullAnswerIntoAFailedFuture() throws Exception {
        when(purgeUeHandler.handle(any())).thenReturn(CompletableFuture.completedFuture(null));

        final var future = registeredPurgeUeHandler().handle(buildRequest());

        assertThat(future).isCompletedExceptionally();
        assertThatThrownBy(future::join)
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("null answer");
    }

    @Test
    void itPassesTheAnswerOfAWellBehavedHandlerThroughUnchanged() throws Exception {
        final var request = buildRequest();
        final var answer = DiameterMessageFactory.createAnswer(request, DiameterConstants.RES_DIAMETER_SUCCESS);
        when(purgeUeHandler.handle(any())).thenReturn(CompletableFuture.completedFuture(answer));

        final var future = registeredPurgeUeHandler().handle(request);

        assertThat(future.join()).isSameAs(answer);
    }

    @Test
    void itTurnsAnExecutorRejectionIntoAFailedFuture() throws Exception {
        final var rejection = new RejectedExecutionException("pool saturated");
        final Executor rejectingExecutor = task -> {
            throw rejection;
        };

        // a saturated pool must not throw out of handle() onto the event loop
        final var future = registeredPurgeUeHandler(rejectingExecutor).handle(buildRequest());

        assertThat(future).isCompletedExceptionally();
        assertThatThrownBy(future::join)
                .isInstanceOf(CompletionException.class)
                .hasCause(rejection);
    }

    @Test
    void itDoesNotEmitSwxVendorSpecificAppForDefaultConfig() {
        // GIVEN: no SWx among the advertised applications
        final var authApplicationIds = List.of((long) CxDxConstants.APP_ID_CX_DX, (long) S6aConstants.APP_ID_S6A_S6D);

        // WHEN
        final var vendorSpecificApps = DiameterConnectionHandler.vendorSpecificApps(authApplicationIds);

        // THEN: no VendorSpecificApp for SWx
        for (final var vsa : vendorSpecificApps) {
            assertThat(vsa.authApplicationId()).isNotEqualTo((long) SwxConstants.APP_ID_SWX);
        }
    }


    @Test
    void itEmitsSwxVendorSpecificAppWhenSwxIsConfigured() {
        // GIVEN
        final var authApplicationIds = List.of(
            (long) CxDxConstants.APP_ID_CX_DX, (long) S6aConstants.APP_ID_S6A_S6D, (long) SwxConstants.APP_ID_SWX);

        // WHEN
        final var vendorSpecificApps = DiameterConnectionHandler.vendorSpecificApps(authApplicationIds);

        // THEN
        assertThat(vendorSpecificApps)
            .map(DiameterNodeConfig.VendorSpecificApp::authApplicationId)
            .contains((long) SwxConstants.APP_ID_SWX);
    }

    /// A handler runs on the injected executor. The tests use a synchronous one (run-on-caller) so
    /// the wrapped future settles before the assertions; production uses a real worker pool.
    private DiameterConnectionHandler connectionHandler() {
        return connectionHandler(Runnable::run);
    }

    private DiameterConnectionHandler connectionHandler(final Executor inboundExecutor) {
        // The constructor consumes every stub right away (duplicate detection reads
        // requestType()), so strict stubbing stays happy in tests that never construct one.
        when(authenticationInfoHandler.requestType()).thenReturn(AuthenticationInformationRequest.In.class);
        when(notifyHandler.requestType()).thenReturn(NotifyRequest.In.class);
        when(purgeUeHandler.requestType()).thenReturn(PurgeUeRequest.In.class);
        when(updateLocationHandler.requestType()).thenReturn(UpdateLocationRequest.In.class);
        when(multimediaAuthHandler.requestType()).thenReturn(com.sipgate.sparta.diameter._3gpp.cxdx.messages.MultimediaAuthRequest.In.class);
        when(serverAssignmentHandler.requestType()).thenReturn(com.sipgate.sparta.diameter._3gpp.cxdx.messages.ServerAssignmentRequest.In.class);
        when(swxMultimediaAuthHandler.requestType()).thenReturn(com.sipgate.sparta.diameter._3gpp.swx.messages.MultimediaAuthRequest.In.class);
        when(swxServerAssignmentHandler.requestType()).thenReturn(com.sipgate.sparta.diameter._3gpp.swx.messages.ServerAssignmentRequest.In.class);
        return new DiameterConnectionHandler(
                config,
                peers,
                meterRegistry,
                diameterSessions,
                inboundExecutor,
                List.of(
                    multimediaAuthHandler,
                    serverAssignmentHandler,
                    authenticationInfoHandler,
                    notifyHandler,
                    purgeUeHandler,
                    updateLocationHandler,
                    swxMultimediaAuthHandler,
                    swxServerAssignmentHandler));
    }

    /// Captures the (wrapped) handler that registerHandlers actually registers for PUR,
    /// so the guard is tested exactly as it sits in front of the lib's dispatch.
    private DiameterRequestHandler<PurgeUeRequest.In, PurgeUeAnswer.Out> registeredPurgeUeHandler() {
        return registeredPurgeUeHandler(Runnable::run);
    }

    @SuppressWarnings("unchecked")
    private DiameterRequestHandler<PurgeUeRequest.In, PurgeUeAnswer.Out> registeredPurgeUeHandler(final Executor inboundExecutor) {
        final var session = mock(DiameterInitiatorSession.class);
        connectionHandler(inboundExecutor).registerHandlers(session);

        final var captor = ArgumentCaptor.forClass(DiameterRequestHandler.class);
        verify(session).setHandler(eq(PurgeUeRequest.In.class), captor.capture());
        return captor.getValue();
    }

    /// Wire-parsed .In commands are immutable: assemble as outgoing, serialize, parse back —
    /// the same way the stack produces inbound requests at runtime.
    private static PurgeUeRequest.In buildRequest() throws Exception {
        final var out = new PurgeUeRequest.Out();
        out.setUserName("999990000104857");
        out.setOriginHost("mmec24.mmegi8001.mme.epc.mnc003.mcc262.3gppnetwork.org");
        out.setOriginRealm("epc.mnc003.mcc262.3gppnetwork.org");

        final var baos = new ByteArrayOutputStream();
        out.writeTo(new DataOutputStream(baos), new HopByHopId(1), new EndToEndId(2));
        return (PurgeUeRequest.In) Command.parseMessage(ByteBuffer.wrap(baos.toByteArray()));
    }
}
