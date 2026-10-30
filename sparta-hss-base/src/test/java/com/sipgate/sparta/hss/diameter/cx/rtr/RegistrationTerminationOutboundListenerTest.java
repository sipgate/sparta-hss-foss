package com.sipgate.sparta.hss.diameter.cx.rtr;

import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.VENDOR_ID_3GPP;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.APP_ID_CX_DX;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.AUTH_SESSION_STATE_NOT_MAINTAINED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants;
import com.sipgate.sparta.diameter._3gpp.cxdx.messages.RegistrationTerminationRequest;
import com.sipgate.sparta.diameter.base.core.avp.AVPKey;
import com.sipgate.sparta.diameter.base.core.avp.GroupedAVP;
import com.sipgate.sparta.hss.diameter.client.DiameterSessions;
import com.sipgate.sparta.hss.diameter.common.Factory;
import com.sipgate.sparta.hss.persistence.ImsiScscfDao;
import com.sipgate.sparta.hss.persistence.LocationIpSmGwDao;
import com.sipgate.sparta.hss.persistence.entities.ImsiScscf;
import com.sipgate.sparta.hss.service.accessPolicy.AccessPolicy;
import com.sipgate.sparta.hss.service.subscription.SimDeactivated;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegistrationTerminationOutboundListenerTest {

    private static final String IMSI = "262021234567890";
    private static final String SECOND_IMSI = "262029876543210";
    private static final String MSISDN = "4912345678901";

    private DiameterSessions diameterSessions;
    private RegistrationTerminationOutboundListener underTest;

    @Mock
    private ImsiScscfDao imsiScscfDao;

    @Mock
    private LocationIpSmGwDao locationIpSmGwDao;

    @Mock
    private AccessPolicy accessPolicy;

    @BeforeEach
    void setUp() {
        diameterSessions = spy(new DiameterSessions(Factory.nodeConfig("origin-host", "origin-realm")));
        underTest = new RegistrationTerminationOutboundListener(diameterSessions, imsiScscfDao, locationIpSmGwDao, accessPolicy);
    }

    @Test
    void itSendsAnRtrWithPermanentTerminationAndClearsBothRows() {
        // GIVEN — the access policy denies IMS access for the deactivated SIM's IMSI
        doReturn(CompletableFuture.completedFuture(null)).when(diameterSessions).send(any());
        when(accessPolicy.isImsAccessAllowed(IMSI)).thenReturn(Optional.of(false));
        final var scscf = new ImsiScscf();
        scscf.setDiameterHost("scscf.example.org");
        scscf.setDiameterRealm("ims.mnc002.mcc262.3gppnetwork.org");
        when(imsiScscfDao.getScscf(IMSI)).thenReturn(Optional.of(scscf));

        // WHEN
        underTest.receiveSimDeactivated(new SimDeactivated("iccid", MSISDN, List.of(IMSI)));

        // THEN
        final var captor = ArgumentCaptor.forClass(RegistrationTerminationRequest.Out.class);
        verify(diameterSessions).send(captor.capture());
        final var rtr = captor.getValue();
        // Private Identity is the IMSI, as in the existing RTR from MultimediaAuthHandler
        assertThat(rtr.getUserName()).isEqualTo(IMSI);
        assertThat(rtr.getPublicIdentities()).containsExactly("tel:+" + MSISDN);
        assertThat(rtr.getDestinationHost()).isEqualTo("scscf.example.org");
        assertThat(rtr.getDestinationRealm()).isEqualTo("ims.mnc002.mcc262.3gppnetwork.org");
        final var reason = (GroupedAVP) rtr.findAVP(new AVPKey(CxDxConstants.AVP_DEREGISTRATION_REASON,
            com.sipgate.sparta.diameter._3gpp.common._3gppConstants.VENDOR_ID_3GPP));
        assertThat(reason.findAVP(new AVPKey(CxDxConstants.AVP_REASON_CODE,
            com.sipgate.sparta.diameter._3gpp.common._3gppConstants.VENDOR_ID_3GPP)).getDataAsInt())
            .isEqualTo(CxDxConstants.REASON_CODE_PERMANENT_TERMINATION);

        verify(imsiScscfDao).clearScscf(IMSI);
        verify(locationIpSmGwDao).clearIpSmGw(IMSI);
    }

    @Test
    void itStillClearsIpSmGwWithoutAStoredScscf() {
        when(accessPolicy.isImsAccessAllowed(IMSI)).thenReturn(Optional.of(false));
        when(imsiScscfDao.getScscf(IMSI)).thenReturn(Optional.empty());

        underTest.receiveSimDeactivated(new SimDeactivated("iccid", MSISDN, List.of(IMSI)));

        verify(diameterSessions, never()).send(any());
        verify(locationIpSmGwDao).clearIpSmGw(IMSI);
        verify(imsiScscfDao, never()).clearScscf(IMSI);
    }

    @Test
    void itSendsNothingWithoutImsis() {
        underTest.receiveSimDeactivated(new SimDeactivated("iccid", MSISDN, List.of()));

        verify(diameterSessions, never()).send(any());
        verify(locationIpSmGwDao, never()).clearIpSmGw(any());
    }

    @Test
    void itSendsNothingAndKeepsBothRowsWhenTheAccessPolicyAllowsTheImsi() {
        // GIVEN
        when(accessPolicy.isImsAccessAllowed(IMSI)).thenReturn(Optional.of(true));

        // WHEN
        underTest.receiveSimDeactivated(new SimDeactivated("iccid", MSISDN, List.of(IMSI)));

        // THEN — no RTR and no clearing, the registration stays as it is
        verify(diameterSessions, never()).send(any());
        verify(locationIpSmGwDao, never()).clearIpSmGw(IMSI);
        verify(imsiScscfDao, never()).clearScscf(IMSI);
    }

    @Test
    void itTerminatesTheImsiWhenTheAccessPolicyCannotDecide() {
        // GIVEN
        when(accessPolicy.isImsAccessAllowed(IMSI)).thenReturn(Optional.empty());
        when(imsiScscfDao.getScscf(IMSI)).thenReturn(Optional.empty());

        underTest.receiveSimDeactivated(new SimDeactivated("iccid", MSISDN, List.of(IMSI)));

        verify(diameterSessions, never()).send(any());
        verify(locationIpSmGwDao).clearIpSmGw(IMSI);
    }

    @Test
    void itTerminatesOnlyTheSingleImsiTheAccessPolicyDenies() {
        // GIVEN
        doReturn(CompletableFuture.completedFuture(null)).when(diameterSessions).send(any());
        when(accessPolicy.isImsAccessAllowed(IMSI)).thenReturn(Optional.of(true));
        when(accessPolicy.isImsAccessAllowed(SECOND_IMSI)).thenReturn(Optional.of(false));
        when(imsiScscfDao.getScscf(SECOND_IMSI)).thenReturn(Optional.of(scscfFor(SECOND_IMSI)));

        // WHEN
        underTest.receiveSimDeactivated(new SimDeactivated("iccid", MSISDN, List.of(IMSI, SECOND_IMSI)));

        // THEN
        final var captor = ArgumentCaptor.forClass(RegistrationTerminationRequest.Out.class);
        verify(diameterSessions, times(1)).send(captor.capture());
        assertThat(captor.getValue().getUserName()).isEqualTo(SECOND_IMSI);
        verify(locationIpSmGwDao, never()).clearIpSmGw(IMSI);
        verify(imsiScscfDao, never()).clearScscf(IMSI);
        verify(locationIpSmGwDao).clearIpSmGw(SECOND_IMSI);
        verify(imsiScscfDao).clearScscf(SECOND_IMSI);
    }

    @Test
    void itTerminatesEveryImsiWhenTheAccessPolicyDeniesAllOfThem() {
        // GIVEN
        doReturn(CompletableFuture.completedFuture(null)).when(diameterSessions).send(any());
        when(accessPolicy.isImsAccessAllowed(IMSI)).thenReturn(Optional.of(false));
        when(accessPolicy.isImsAccessAllowed(SECOND_IMSI)).thenReturn(Optional.of(false));
        when(imsiScscfDao.getScscf(IMSI)).thenReturn(Optional.of(scscfFor(IMSI)));
        when(imsiScscfDao.getScscf(SECOND_IMSI)).thenReturn(Optional.of(scscfFor(SECOND_IMSI)));

        // WHEN
        underTest.receiveSimDeactivated(new SimDeactivated("iccid", MSISDN, List.of(IMSI, SECOND_IMSI)));

        // THEN
        final var captor = ArgumentCaptor.forClass(RegistrationTerminationRequest.Out.class);
        verify(diameterSessions, times(2)).send(captor.capture());
        assertThat(captor.getAllValues()).extracting(RegistrationTerminationRequest.Out::getUserName)
            .containsExactlyInAnyOrder(IMSI, SECOND_IMSI);
        verify(locationIpSmGwDao).clearIpSmGw(IMSI);
        verify(locationIpSmGwDao).clearIpSmGw(SECOND_IMSI);
        verify(imsiScscfDao).clearScscf(IMSI);
        verify(imsiScscfDao).clearScscf(SECOND_IMSI);
    }

    @Test
    void itSwallowsAFailingTeardownAndStillTerminatesTheRemainingImsis() {
        // GIVEN — the first IMSI's S-CSCF lookup fails (db error); the second IMSI is healthy.
        // The teardown runs synchronously in the publisher's transaction, so a propagating
        // exception would mark that transaction rollback-only and lose the sim deactivation —
        // it must be caught inside the listener instead.
        doReturn(CompletableFuture.completedFuture(null)).when(diameterSessions).send(any());
        when(accessPolicy.isImsAccessAllowed(IMSI)).thenReturn(Optional.of(false));
        when(accessPolicy.isImsAccessAllowed(SECOND_IMSI)).thenReturn(Optional.of(false));
        when(imsiScscfDao.getScscf(IMSI)).thenThrow(new IllegalStateException("db down"));
        when(imsiScscfDao.getScscf(SECOND_IMSI)).thenReturn(Optional.of(scscfFor(SECOND_IMSI)));

        // WHEN + THEN — nothing propagates and the healthy IMSI is still de-registered
        assertThatCode(() ->
            underTest.receiveSimDeactivated(new SimDeactivated("iccid", MSISDN, List.of(IMSI, SECOND_IMSI))))
            .doesNotThrowAnyException();
        final var captor = ArgumentCaptor.forClass(RegistrationTerminationRequest.Out.class);
        verify(diameterSessions, times(1)).send(captor.capture());
        assertThat(captor.getValue().getUserName()).isEqualTo(SECOND_IMSI);
        verify(locationIpSmGwDao).clearIpSmGw(IMSI);
        verify(imsiScscfDao).clearScscf(SECOND_IMSI);
    }

    @Test
    void itSwallowsAFailingSendOfARegistrationTerminationEvent() {
        // GIVEN — the RTR for an S-CSCF reassignment cannot be sent; the triggering inbound
        // request (e.g. MAR) must not fail because of it
        final var rtr = diameterSessions.createRequest(
            RegistrationTerminationRequest.Out.class, AUTH_SESSION_STATE_NOT_MAINTAINED,
            "scscf.example.org", "ims.mnc002.mcc262.3gppnetwork.org", VENDOR_ID_3GPP, APP_ID_CX_DX);
        doThrow(new IllegalStateException("send failed")).when(diameterSessions).send(any());

        // WHEN + THEN
        assertThatCode(() -> underTest.receiveRequest(new RegistrationTerminationEvent(rtr, "scscfChange")))
            .doesNotThrowAnyException();
        verify(diameterSessions, times(1)).send(any());
    }

    private static ImsiScscf scscfFor(final String imsi) {
        final var scscf = new ImsiScscf();
        scscf.setDiameterHost("scscf-" + imsi + ".example.org");
        scscf.setDiameterRealm("ims.mnc002.mcc262.3gppnetwork.org");
        return scscf;
    }
}
