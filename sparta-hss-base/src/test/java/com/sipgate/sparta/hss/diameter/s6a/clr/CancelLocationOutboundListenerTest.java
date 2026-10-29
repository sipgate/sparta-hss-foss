package com.sipgate.sparta.hss.diameter.s6a.clr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.sipgate.sparta.diameter._3gpp.s6a.S6aConstants;
import com.sipgate.sparta.diameter._3gpp.s6a.messages.CancelLocationRequest;
import com.sipgate.sparta.hss.diameter.client.DiameterSessions;
import com.sipgate.sparta.hss.diameter.common.Factory;
import com.sipgate.sparta.hss.persistence.LocationLteDao;
import com.sipgate.sparta.hss.persistence.entities.LocationLTE;
import com.sipgate.sparta.hss.service.subscription.SimDeactivated;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class CancelLocationOutboundListenerTest {

    private static final String IMSI = "262021234567890";
    private static final String MSISDN = "4912345678901";

    private DiameterSessions diameterSessions;
    private CancelLocationOutboundListener underTest;

    @Mock
    private LocationLteDao locationLteDao;

    @BeforeEach
    void setUp() {
        diameterSessions = spy(new DiameterSessions(Factory.nodeConfig("origin-host", "origin-realm")));
        underTest = new CancelLocationOutboundListener(diameterSessions, locationLteDao);
    }

    @Test
    void itSendsAClrWithSubscriptionWithdrawalToTheStoredMmeAndRemovesTheRow() {
        // GIVEN
    doReturn(CompletableFuture.completedFuture(null)).when(diameterSessions).send(any());
        final var location = new LocationLTE()
            .setMmeHostname("mme.example.org")
            .setMmeRealm("epc.mnc002.mcc262.3gppnetwork.org");
        org.mockito.Mockito.when(locationLteDao.getLocation(IMSI)).thenReturn(Optional.of(location));

        // WHEN
        underTest.receiveSimDeactivated(new SimDeactivated("iccid", MSISDN, List.of(IMSI)));

        // THEN
        final var captor = ArgumentCaptor.forClass(CancelLocationRequest.Out.class);
        verify(diameterSessions).send(captor.capture());
        final var clr = captor.getValue();
        assertThat(clr.getUserName()).isEqualTo(IMSI);
        assertThat(clr.getCancellationType()).isEqualTo(S6aConstants.CANCELLATION_TYPE_SUBSCRIPTION_WITHDRAWAL);
        // only the S6a/S6d-Indicator bit — the re-attach flag must stay unset (TS 29.272 §7.3.152)
        assertThat(clr.getClrFlags()).isEqualTo(1L);
        assertThat(clr.getDestinationHost()).isEqualTo("mme.example.org");
        assertThat(clr.getDestinationRealm()).isEqualTo("epc.mnc002.mcc262.3gppnetwork.org");
        verify(locationLteDao).removeExistingMmeLocation(location);
    }

    @Test
    void itSendsNothingAndKeepsTheTableUntouchedWithoutAStoredMme() {
        org.mockito.Mockito.when(locationLteDao.getLocation(IMSI)).thenReturn(Optional.empty());

        underTest.receiveSimDeactivated(new SimDeactivated("iccid", MSISDN, List.of(IMSI)));

        verify(diameterSessions, never()).send(any());
        verify(locationLteDao, never()).removeExistingMmeLocation(any());
    }

    @Test
    void itSendsOneClrPerImsiOfTheSim() {
        final var secondImsi = "262029876543210";
        org.mockito.Mockito.when(locationLteDao.getLocation(IMSI)).thenReturn(Optional.of(
            new LocationLTE().setMmeHostname("mme.example.org").setMmeRealm("epc.example.org")));
        org.mockito.Mockito.when(locationLteDao.getLocation(secondImsi)).thenReturn(Optional.of(
            new LocationLTE().setMmeHostname("mme2.example.org").setMmeRealm("epc.example.org")));

        underTest.receiveSimDeactivated(new SimDeactivated("iccid", MSISDN, List.of(IMSI, secondImsi)));

        verify(diameterSessions, org.mockito.Mockito.times(2)).send(any());
        verify(locationLteDao, org.mockito.Mockito.times(2)).removeExistingMmeLocation(any());
    }
}
