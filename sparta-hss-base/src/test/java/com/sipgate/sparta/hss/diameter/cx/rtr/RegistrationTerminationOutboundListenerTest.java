package com.sipgate.sparta.hss.diameter.cx.rtr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
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

/// The listener owns the `imsi_scscf` and `location_ip_sm_gw` lifecycle on deactivation: it
/// fetches the stored S-CSCF itself, sends the RTR, then clears the rows — the event carries
/// only the subscriber identity.
@ExtendWith(MockitoExtension.class)
class RegistrationTerminationOutboundListenerTest {

    private static final String IMSI = "262021234567890";
    private static final String MSISDN = "4912345678901";

    private DiameterSessions diameterSessions;
    private RegistrationTerminationOutboundListener underTest;

    @Mock
    private ImsiScscfDao imsiScscfDao;

    @Mock
    private LocationIpSmGwDao locationIpSmGwDao;

    @BeforeEach
    void setUp() {
        diameterSessions = spy(new DiameterSessions(Factory.nodeConfig("origin-host", "origin-realm")));
        underTest = new RegistrationTerminationOutboundListener(diameterSessions, imsiScscfDao, locationIpSmGwDao);
    }

    @Test
    void itSendsAnRtrWithPermanentTerminationAndClearsBothRows() {
        // GIVEN
    doReturn(CompletableFuture.completedFuture(null)).when(diameterSessions).send(any());
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
}
