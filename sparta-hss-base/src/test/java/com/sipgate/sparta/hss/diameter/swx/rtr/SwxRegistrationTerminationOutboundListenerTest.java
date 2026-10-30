package com.sipgate.sparta.hss.diameter.swx.rtr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants;
import com.sipgate.sparta.diameter._3gpp.swx.messages.RegistrationTerminationRequest;
import com.sipgate.sparta.diameter.base.core.avp.AVPKey;
import com.sipgate.sparta.diameter.base.core.avp.GroupedAVP;
import com.sipgate.sparta.hss.diameter.client.DiameterSessions;
import com.sipgate.sparta.hss.diameter.common.Factory;
import com.sipgate.sparta.hss.persistence.LocationVowifiDao;
import com.sipgate.sparta.hss.persistence.entities.LocationVowifi;
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
class SwxRegistrationTerminationOutboundListenerTest {

    private static final String IMSI = "262021234567890";
    private static final String SECOND_IMSI = "262029876543210";
    private static final String MSISDN = "4912345678901";

    private DiameterSessions diameterSessions;
    private SwxRegistrationTerminationOutboundListener underTest;

    @Mock
    private LocationVowifiDao locationVowifiDao;

    @Mock
    private AccessPolicy accessPolicy;

    @BeforeEach
    void setUp() {
        diameterSessions = spy(new DiameterSessions(Factory.nodeConfig("origin-host", "origin-realm")));
        underTest = new SwxRegistrationTerminationOutboundListener(diameterSessions, locationVowifiDao, accessPolicy);
    }

    @Test
    void itSendsAnRtrWithPermanentTerminationToTheStoredAaaServerAndClearsIt() {
        // GIVEN
        doReturn(CompletableFuture.completedFuture(null)).when(diameterSessions).send(any());
        when(accessPolicy.isImsAccessAllowed(IMSI)).thenReturn(Optional.of(false));
        final var aaaServer = new LocationVowifi();
        aaaServer.setDiameterHost("aaa.example.org");
        aaaServer.setDiameterRealm("wlan.mnc002.mcc262.3gppnetwork.org");
        when(locationVowifiDao.getAaaServer(IMSI)).thenReturn(Optional.of(aaaServer));

        // WHEN
        underTest.receiveSimDeactivated(new SimDeactivated("iccid", MSISDN, List.of(IMSI)));

        // THEN
        final var captor = ArgumentCaptor.forClass(RegistrationTerminationRequest.Out.class);
        verify(diameterSessions).send(captor.capture());
        final var rtr = captor.getValue();
        assertThat(rtr.getUserName()).isEqualTo(IMSI);
        assertThat(rtr.getDestinationHost()).isEqualTo("aaa.example.org");
        assertThat(rtr.getDestinationRealm()).isEqualTo("wlan.mnc002.mcc262.3gppnetwork.org");
        final var reason = (GroupedAVP) rtr.findAVP(new AVPKey(CxDxConstants.AVP_DEREGISTRATION_REASON,
            com.sipgate.sparta.diameter._3gpp.common._3gppConstants.VENDOR_ID_3GPP));
        assertThat(reason.findAVP(new AVPKey(CxDxConstants.AVP_REASON_CODE,
            com.sipgate.sparta.diameter._3gpp.common._3gppConstants.VENDOR_ID_3GPP)).getDataAsInt())
            .isEqualTo(CxDxConstants.REASON_CODE_PERMANENT_TERMINATION);

        verify(locationVowifiDao).clearAaaServer(IMSI);
    }

    @Test
    void itSendsNothingWithoutAStoredAaaServer() {
        when(accessPolicy.isImsAccessAllowed(IMSI)).thenReturn(Optional.of(false));
        when(locationVowifiDao.getAaaServer(IMSI)).thenReturn(Optional.empty());

        underTest.receiveSimDeactivated(new SimDeactivated("iccid", MSISDN, List.of(IMSI)));

        verify(diameterSessions, never()).send(any());
        verify(locationVowifiDao, never()).clearAaaServer(IMSI);
    }

    @Test
    void itSendsNothingAndKeepsTheAssignmentWhenTheAccessPolicyAllowsTheImsi() {
        // GIVEN
        when(accessPolicy.isImsAccessAllowed(IMSI)).thenReturn(Optional.of(true));

        // WHEN
        underTest.receiveSimDeactivated(new SimDeactivated("iccid", MSISDN, List.of(IMSI)));

        // THEN
        verify(diameterSessions, never()).send(any());
        verify(locationVowifiDao, never()).getAaaServer(IMSI);
        verify(locationVowifiDao, never()).clearAaaServer(IMSI);
    }

    @Test
    void itTerminatesTheImsiWhenTheAccessPolicyCannotDecide() {
        // GIVEN
        when(accessPolicy.isImsAccessAllowed(IMSI)).thenReturn(Optional.empty());
        when(locationVowifiDao.getAaaServer(IMSI)).thenReturn(Optional.empty());

        underTest.receiveSimDeactivated(new SimDeactivated("iccid", MSISDN, List.of(IMSI)));

        verify(diameterSessions, never()).send(any());
        verify(locationVowifiDao).getAaaServer(IMSI);
    }

    @Test
    void itTerminatesOnlyTheSingleImsiTheAccessPolicyDenies() {
        // GIVEN
        doReturn(CompletableFuture.completedFuture(null)).when(diameterSessions).send(any());
        when(accessPolicy.isImsAccessAllowed(IMSI)).thenReturn(Optional.of(true));
        when(accessPolicy.isImsAccessAllowed(SECOND_IMSI)).thenReturn(Optional.of(false));
        when(locationVowifiDao.getAaaServer(SECOND_IMSI)).thenReturn(Optional.of(aaaServerFor(SECOND_IMSI)));

        // WHEN
        underTest.receiveSimDeactivated(new SimDeactivated("iccid", MSISDN, List.of(IMSI, SECOND_IMSI)));

        // THEN
        final var captor = ArgumentCaptor.forClass(RegistrationTerminationRequest.Out.class);
        verify(diameterSessions, times(1)).send(captor.capture());
        assertThat(captor.getValue().getUserName()).isEqualTo(SECOND_IMSI);
        verify(locationVowifiDao, never()).getAaaServer(IMSI);
        verify(locationVowifiDao, never()).clearAaaServer(IMSI);
        verify(locationVowifiDao).clearAaaServer(SECOND_IMSI);
    }

    @Test
    void itTerminatesEveryImsiWhenTheAccessPolicyDeniesAllOfThem() {
        // GIVEN
        doReturn(CompletableFuture.completedFuture(null)).when(diameterSessions).send(any());
        when(accessPolicy.isImsAccessAllowed(IMSI)).thenReturn(Optional.of(false));
        when(accessPolicy.isImsAccessAllowed(SECOND_IMSI)).thenReturn(Optional.of(false));
        when(locationVowifiDao.getAaaServer(IMSI)).thenReturn(Optional.of(aaaServerFor(IMSI)));
        when(locationVowifiDao.getAaaServer(SECOND_IMSI)).thenReturn(Optional.of(aaaServerFor(SECOND_IMSI)));

        // WHEN
        underTest.receiveSimDeactivated(new SimDeactivated("iccid", MSISDN, List.of(IMSI, SECOND_IMSI)));

        // THEN
        final var captor = ArgumentCaptor.forClass(RegistrationTerminationRequest.Out.class);
        verify(diameterSessions, times(2)).send(captor.capture());
        assertThat(captor.getAllValues()).extracting(RegistrationTerminationRequest.Out::getUserName)
            .containsExactlyInAnyOrder(IMSI, SECOND_IMSI);
        verify(locationVowifiDao).clearAaaServer(IMSI);
        verify(locationVowifiDao).clearAaaServer(SECOND_IMSI);
    }

    private static LocationVowifi aaaServerFor(final String imsi) {
        final var aaaServer = new LocationVowifi();
        aaaServer.setDiameterHost("aaa-" + imsi + ".example.org");
        aaaServer.setDiameterRealm("wlan.mnc002.mcc262.3gppnetwork.org");
        return aaaServer;
    }
}
