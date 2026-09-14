package com.sipgate.sparta.hss.diameter.s6a.nir;

import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.EXP_RES_DIAMETER_ERROR_USER_UNKNOWN;
import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.VENDOR_ID_3GPP;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.AVP_EXPERIMENTAL_RESULT_CODE;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.AVP_VENDOR_ID;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.RES_DIAMETER_SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.sipgate.sparta.diameter._3gpp.s6a.messages.NotifyRequest;
import com.sipgate.sparta.diameter.base.core.DiameterConstants;
import com.sipgate.sparta.diameter.base.core.avp.AVPContainer;
import com.sipgate.sparta.diameter.base.core.Answer;
import com.sipgate.sparta.diameter.base.core.Command;
import com.sipgate.sparta.diameter.base.core.EndToEndId;
import com.sipgate.sparta.diameter.base.core.HopByHopId;
import com.sipgate.sparta.diameter.base.core.avp.AVPKey;
import com.sipgate.sparta.diameter.base.session.DiameterErrorAnswerException;
import com.sipgate.sparta.hss.persistence.SimDao;
import com.sipgate.sparta.hss.persistence.entities.Sim;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotifyHandlerTest {

    private static final AVPKey KEY_VENDOR_ID = new AVPKey(AVP_VENDOR_ID, 0);
    private static final AVPKey KEY_EXPERIMENTAL_RESULT_CODE = new AVPKey(AVP_EXPERIMENTAL_RESULT_CODE, 0);
    private static final String IMSI = "262011234567890";

    @InjectMocks
    private NotifyHandler underTest;

    @Mock
    private SimDao simDao;

    @Test
    void itReturnsDiameterSuccessForKnownSubscribers() throws Exception {
        // GIVEN
        when(simDao.getSimByImsiString(anyString())).thenReturn(Optional.of(mock(Sim.class)));

        // WHEN
        final var answer = underTest.handle(buildRequest(IMSI)).join();

        // THEN
        assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_SUCCESS);
    }

    @Test
    void itReturnsUserUnknownWhenSubscriberIsUnknown() throws Exception {
        // GIVEN
        when(simDao.getSimByImsiString(anyString())).thenReturn(Optional.empty());

        // WHEN
        final var answer = unwrapErrorAnswer(underTest.handle(buildRequest(IMSI)));

        // THEN
        assertExperimentalErrorAnswer(answer, EXP_RES_DIAMETER_ERROR_USER_UNKNOWN);
    }

    /**
     * Builds an incoming Notify-Request carrying the IMSI as User-Name. A wire-parsed {@code .In}
     * command is immutable, so the request is assembled as an outgoing message, serialized and parsed
     * back — exactly how the stack produces inbound requests at runtime.
     */
    private static NotifyRequest.In buildRequest(final String imsi) throws Exception {
        final var out = new NotifyRequest.Out();
        out.setUserName(imsi);

        final var baos = new ByteArrayOutputStream();
        out.writeTo(new DataOutputStream(baos), new HopByHopId(1), new EndToEndId(2));
        return (NotifyRequest.In) Command.parseMessage(ByteBuffer.wrap(baos.toByteArray()));
    }

    private static Answer unwrapErrorAnswer(final CompletableFuture<?> future) {
        return ((DiameterErrorAnswerException) future.exceptionNow()).getAnswer();
    }

    private static void assertExperimentalErrorAnswer(final Answer answer, final long expectedResultCode) {
        assertThat(answer.getResultCode()).as("no base Result-Code for 3GPP results").isEqualTo(-1L);
        assertThat(answer.isError()).as("an Experimental-Result must not set the E-bit (RFC 6733 §7.6)").isFalse();
        final var experimentalResult = (AVPContainer) answer.findAVP(new AVPKey(DiameterConstants.AVP_EXPERIMENTAL_RESULT, 0));
        assertThat(experimentalResult).as("Experimental-Result grouped AVP").isNotNull();
        assertThat(experimentalResult.findAVP(KEY_VENDOR_ID).getDataAsUnsignedInt()).isEqualTo(VENDOR_ID_3GPP);
        assertThat(experimentalResult.findAVP(KEY_EXPERIMENTAL_RESULT_CODE).getDataAsUnsignedInt())
                .isEqualTo(expectedResultCode);
    }
}
