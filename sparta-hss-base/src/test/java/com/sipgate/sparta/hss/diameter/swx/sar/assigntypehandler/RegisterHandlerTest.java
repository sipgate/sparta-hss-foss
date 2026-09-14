package com.sipgate.sparta.hss.diameter.swx.sar.assigntypehandler;

import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.SERVER_ASSIGNMENT_REGISTRATION;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.RES_DIAMETER_SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sipgate.sparta.diameter._3gpp.swx.messages.ServerAssignmentRequest;
import com.sipgate.sparta.diameter.base.core.Command;
import com.sipgate.sparta.diameter.base.core.EndToEndId;
import com.sipgate.sparta.diameter.base.core.HopByHopId;
import com.sipgate.sparta.diameter.base.core.avp.AVP;
import com.sipgate.sparta.hss.diameter.swx.sar.Non3gppUserDataFactory;
import com.sipgate.sparta.hss.diameter.swx.sar.AaaServerAssignmentChanged;
import com.sipgate.sparta.hss.event.EventPublisher;
import com.sipgate.sparta.hss.persistence.ImsiProfileDao;
import com.sipgate.sparta.hss.persistence.LocationVowifiDao;
import com.sipgate.sparta.hss.persistence.SimDao;
import com.sipgate.sparta.hss.persistence.entities.LocationVowifi;
import com.sipgate.sparta.hss.persistence.entities.Sim;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegisterHandlerTest {

    private static final String IMSI = "999990000127010";
    private static final String USERNAME = IMSI + "@ims.mnc003.mcc262.3gppnetwork.org";
    private static final String AAA_HOST = "aaa-server.epc.mnc999.mcc999.3gppnetwork.org";
    private static final String REALM = "epc.mnc003.mcc262.3gppnetwork.org";

    @Mock private SimDao simDao;
    @Mock private LocationVowifiDao locationVowifiDao;
    @Mock private ImsiProfileDao imsiProfileDao;
    @Mock private Non3gppUserDataFactory non3gppUserDataFactory;
    @Mock private EventPublisher eventPublisher;

    private RegisterHandler underTest;

    @BeforeEach
    void setUp() {
        underTest = new RegisterHandler(
            simDao, locationVowifiDao, imsiProfileDao, non3gppUserDataFactory, eventPublisher);
    }

    private static LocationVowifi storedAaa(final String name) {
        final var v = new LocationVowifi();
        v.setAaaServerName(name);
        return v;
    }

    // Copied verbatim from SwxServerAssignmentHandlerTest#buildSar (wire round-trip; null params omit the AVP).
    private static ServerAssignmentRequest.In buildSar(
        final String username, final Integer sat, final String originHost, final String originRealm) throws Exception
    {
        final var out = new ServerAssignmentRequest.Out();
        if (username != null) { out.setUserName(username); }
        if (sat != null) { out.setServerAssignmentType(sat); }
        if (originHost != null) { out.setOriginHost(originHost); }
        if (originRealm != null) { out.setOriginRealm(originRealm); }
        final var baos = new ByteArrayOutputStream();
        out.writeTo(new DataOutputStream(baos), new HopByHopId(1), new EndToEndId(2));
        return (ServerAssignmentRequest.In) Command.parseMessage(ByteBuffer.wrap(baos.toByteArray()));
    }

    @Test
    void itReturnsSuccessWithNon3gppUserDataOnIdempotentReRegistration() throws Exception {
        // GIVEN — MAR pre-stored this AAA; same Origin-Host → idempotent re-registration
        when(simDao.getSimByImsiString(IMSI)).thenReturn(Optional.of(new Sim()));
        when(locationVowifiDao.getAaaServer(IMSI)).thenReturn(Optional.of(storedAaa(AAA_HOST)));
        when(imsiProfileDao.findByImsi(IMSI)).thenReturn(Optional.of("default"));
        when(non3gppUserDataFactory.createNon3gppUserData("default", null)).thenReturn(List.<AVP>of());

        // WHEN
        final var answer = underTest.handle(buildSar(USERNAME, SERVER_ASSIGNMENT_REGISTRATION, AAA_HOST, REALM));

        // THEN — SUCCESS, Non-3GPP-User-Data attempted, no new store, no duplicate event
        assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_SUCCESS);
        verify(non3gppUserDataFactory).createNon3gppUserData("default", null);
        verify(locationVowifiDao, never()).store(any(), any(), any(), any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void itStoresAndPublishesWhenNothingPreviouslyStored() throws Exception {
        // GIVEN — REGISTRATION without a prior MAR store
        when(simDao.getSimByImsiString(IMSI)).thenReturn(Optional.of(new Sim()));
        when(locationVowifiDao.getAaaServer(IMSI)).thenReturn(Optional.empty());
        when(imsiProfileDao.findByImsi(IMSI)).thenReturn(Optional.of("default"));
        when(non3gppUserDataFactory.createNon3gppUserData("default", null)).thenReturn(List.<AVP>of());

        // WHEN
        underTest.handle(buildSar(USERNAME, SERVER_ASSIGNMENT_REGISTRATION, AAA_HOST, REALM));

        // THEN
        verify(locationVowifiDao).store(IMSI, AAA_HOST, AAA_HOST, REALM);
        verify(eventPublisher).publish(any(AaaServerAssignmentChanged.class));
    }

    @Test
    void itReturnsUserUnknownWhenSimAbsent() throws Exception {
        // GIVEN
        when(simDao.getSimByImsiString(IMSI)).thenReturn(Optional.empty());
        // WHEN
        final var answer = underTest.handle(buildSar(USERNAME, SERVER_ASSIGNMENT_REGISTRATION, AAA_HOST, REALM));
        // THEN — 5001 experimental; no profile lookup, no store
        assertThat(answer.getExperimentalResult()).isNotNull();
        verifyNoInteractions(non3gppUserDataFactory, eventPublisher);
        verify(locationVowifiDao, never()).store(any(), any(), any(), any());
    }

    @Test
    void itReturns5005AndOldNameWhenADifferentServerIsRegistered() throws Exception {
        // GIVEN — a different AAA already serves this IMSI (SAR has no AAA-Failure-Indication → no override)
        when(simDao.getSimByImsiString(IMSI)).thenReturn(Optional.of(new Sim()));
        when(locationVowifiDao.getAaaServer(IMSI)).thenReturn(Optional.of(storedAaa("other-aaa.example.org")));
        // WHEN
        final var answer = underTest.handle(buildSar(USERNAME, SERVER_ASSIGNMENT_REGISTRATION, AAA_HOST, REALM));
        // THEN — 5005 + the stored (old) name, nothing changed
        assertThat(answer.getExperimentalResult()).isNotNull();
        assertThat(answer.get3gppAaaServerName()).isEqualTo("other-aaa.example.org");
        verify(locationVowifiDao, never()).store(any(), any(), any(), any());
        verifyNoInteractions(non3gppUserDataFactory, eventPublisher);
    }
}
