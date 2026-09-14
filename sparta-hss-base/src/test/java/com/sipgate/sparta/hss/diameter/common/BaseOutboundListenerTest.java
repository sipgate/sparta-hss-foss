package com.sipgate.sparta.hss.diameter.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sipgate.sparta.diameter._3gpp.cxdx.messages.RegistrationTerminationRequest;
import com.sipgate.sparta.hss.diameter.client.DiameterSessions;
import com.sipgate.sparta.hss.diameter.cx.rtr.RegistrationTerminationEvent;
import com.sipgate.sparta.hss.diameter.cx.rtr.RegistrationTerminationOutboundListener;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Verifies the shared [BaseOutboundListener] behaviour through a concrete subclass: it stamps
/// the Session-Id from the session layer and forwards the request to [DiameterSessions].
@ExtendWith(MockitoExtension.class)
class BaseOutboundListenerTest {

    private static final String SESSION_ID = "sparta-hss.example.org;12345;1";

    @Mock
    private DiameterSessions diameterSessions;

    @Test
    void itSendsTheRequest() {
        // GIVEN
        final var listener = new RegistrationTerminationOutboundListener(diameterSessions);
        final var request = new RegistrationTerminationRequest.Out();
        request.setSessionId(SESSION_ID);

        when(diameterSessions.send(any())).thenReturn(CompletableFuture.completedFuture(null));

        // WHEN
        listener.receiveRequest(new RegistrationTerminationEvent(request, "user=test"));

        // THEN
        final var sentCaptor = ArgumentCaptor.forClass(RegistrationTerminationRequest.Out.class);
        verify(diameterSessions).send(sentCaptor.capture());
        assertThat(sentCaptor.getValue()).isSameAs(request);
    }
}
