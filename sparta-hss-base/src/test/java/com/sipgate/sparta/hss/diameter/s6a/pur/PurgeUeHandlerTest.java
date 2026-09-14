package com.sipgate.sparta.hss.diameter.s6a.pur;

import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.EXP_RES_DIAMETER_ERROR_USER_UNKNOWN;
import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.VENDOR_ID_3GPP;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.AVP_EXPERIMENTAL_RESULT_CODE;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.AVP_VENDOR_ID;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.RES_DIAMETER_SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.sipgate.sparta.diameter._3gpp.s6a.messages.PurgeUeRequest;
import com.sipgate.sparta.diameter.base.core.DiameterConstants;
import com.sipgate.sparta.diameter.base.core.avp.AVPContainer;
import com.sipgate.sparta.diameter.base.core.Answer;
import com.sipgate.sparta.diameter.base.core.Command;
import com.sipgate.sparta.diameter.base.core.EndToEndId;
import com.sipgate.sparta.diameter.base.core.HopByHopId;
import com.sipgate.sparta.diameter.base.core.avp.AVPKey;
import com.sipgate.sparta.diameter.base.session.DiameterErrorAnswerException;
import com.sipgate.sparta.hss.diameter.common.Factory;
import com.sipgate.sparta.hss.persistence.LocationLteDao;
import com.sipgate.sparta.hss.persistence.SimDao;
import com.sipgate.sparta.hss.persistence.entities.LocationLTE;
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
class PurgeUeHandlerTest {

    private static final AVPKey KEY_VENDOR_ID = new AVPKey(AVP_VENDOR_ID, 0);
    private static final AVPKey KEY_EXPERIMENTAL_RESULT_CODE = new AVPKey(AVP_EXPERIMENTAL_RESULT_CODE, 0);

    private static final String IMSI = "999990000104857";
    private static final String MME_HOST = "mmec24.mmegi8001.mme.epc.mnc003.mcc262.3gppnetwork.org";
    private static final String MME_REALM = "epc.mnc003.mcc262.3gppnetwork.org";

    @InjectMocks
    private PurgeUeHandler underTest;

    @Mock
    private SimDao simDao;

    @Mock
    private LocationLteDao locationLteDao;

    @Test
    void itReturnsDiameterSuccessAndPurgesLocationWhenStoredMmeMatches() throws Exception {
        // GIVEN
        when(simDao.getSimByImsiString(anyString())).thenReturn(Optional.of(mock(Sim.class)));

        final var storedLocation = new LocationLTE(Factory.buildImsi(), MME_HOST, MME_REALM, "any-vplmn", null);
        when(locationLteDao.getLocation(IMSI)).thenReturn(Optional.of(storedLocation));

        // WHEN
        final var answer = underTest.handle(buildRequest(IMSI, MME_HOST, MME_REALM)).join();

        // THEN
        assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_SUCCESS);
        verify(locationLteDao).removeExistingMmeLocation(storedLocation);
    }

    @Test
    void itKeepsLocationWhenStoredMmeHostDiffers() throws Exception {
        // GIVEN
        when(simDao.getSimByImsiString(anyString())).thenReturn(Optional.of(mock(Sim.class)));

        final var storedLocation = new LocationLTE(Factory.buildImsi(), "other-host", MME_REALM, "any-vplmn", null);
        when(locationLteDao.getLocation(IMSI)).thenReturn(Optional.of(storedLocation));

        // WHEN
        final var answer = underTest.handle(buildRequest(IMSI, MME_HOST, MME_REALM)).join();

        // THEN
        assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_SUCCESS);
        verify(locationLteDao, never()).removeExistingMmeLocation(any(LocationLTE.class));
    }

    @Test
    void itKeepsLocationWhenStoredMmeRealmDiffers() throws Exception {
        // GIVEN
        when(simDao.getSimByImsiString(anyString())).thenReturn(Optional.of(mock(Sim.class)));

        final var storedLocation = new LocationLTE(Factory.buildImsi(), MME_HOST, "other-realm", "any-vplmn", null);
        when(locationLteDao.getLocation(IMSI)).thenReturn(Optional.of(storedLocation));

        // WHEN
        final var answer = underTest.handle(buildRequest(IMSI, MME_HOST, MME_REALM)).join();

        // THEN
        assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_SUCCESS);
        verify(locationLteDao, never()).removeExistingMmeLocation(any(LocationLTE.class));
    }

    @Test
    void itReturnsUserUnknownWhenSubscriberIsUnknown() throws Exception {
        // GIVEN
        when(simDao.getSimByImsiString(anyString())).thenReturn(Optional.empty());

        // WHEN
        final var answer = unwrapErrorAnswer(underTest.handle(buildRequest(IMSI, MME_HOST, MME_REALM)));

        // THEN
        assertExperimentalErrorAnswer(answer, EXP_RES_DIAMETER_ERROR_USER_UNKNOWN);
        verifyNoInteractions(locationLteDao);
    }

    /**
     * Builds an incoming Purge-UE-Request carrying the IMSI as User-Name plus the requesting MME's
     * Origin-Host/Origin-Realm. A wire-parsed {@code .In} command is immutable, so the request is
     * assembled as an outgoing message, serialized and parsed back — exactly how the stack produces
     * inbound requests at runtime.
     */
    private static PurgeUeRequest.In buildRequest(final String imsi, final String originHost, final String originRealm)
            throws Exception {
        final var out = new PurgeUeRequest.Out();
        out.setUserName(imsi);
        out.setOriginHost(originHost);
        out.setOriginRealm(originRealm);

        final var baos = new ByteArrayOutputStream();
        out.writeTo(new DataOutputStream(baos), new HopByHopId(1), new EndToEndId(2));
        return (PurgeUeRequest.In) Command.parseMessage(ByteBuffer.wrap(baos.toByteArray()));
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
