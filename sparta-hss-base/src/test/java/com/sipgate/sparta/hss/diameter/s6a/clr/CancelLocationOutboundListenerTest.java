package com.sipgate.sparta.hss.diameter.s6a.clr;

import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.VENDOR_ID_3GPP;
import static com.sipgate.sparta.diameter._3gpp.s6a.S6aConstants.APP_ID_S6A_S6D;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sipgate.sparta.diameter._3gpp.s6a.S6aConstants;
import com.sipgate.sparta.diameter._3gpp.s6a.messages.CancelLocationRequest;
import com.sipgate.sparta.hss.diameter.client.DiameterSessions;
import com.sipgate.sparta.hss.diameter.common.Factory;
import com.sipgate.sparta.hss.persistence.LocationLteDao;
import com.sipgate.sparta.hss.persistence.entities.LocationLTE;
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
class CancelLocationOutboundListenerTest {

    private static final String IMSI = "262021234567890";
    private static final String SECOND_IMSI = "262029876543210";
    private static final String MSISDN = "4912345678901";
    private static final String VISITED_PLMN = "26202";
    private static final String VISITED_MCC = "262";

    private DiameterSessions diameterSessions;
    private CancelLocationOutboundListener underTest;

    @Mock
    private LocationLteDao locationLteDao;

    @Mock
    private AccessPolicy accessPolicy;

    @BeforeEach
    void setUp() {
        diameterSessions = spy(new DiameterSessions(Factory.nodeConfig("origin-host", "origin-realm")));
        underTest = new CancelLocationOutboundListener(diameterSessions, locationLteDao, accessPolicy);
    }

    @Test
    void itSendsAClrWithSubscriptionWithdrawalToTheStoredMmeAndRemovesTheRow() {
        // GIVEN
        doReturn(CompletableFuture.completedFuture(null)).when(diameterSessions).send(any());
        final var location = new LocationLTE()
            .setMmeHostname("mme.example.org")
            .setMmeRealm("epc.mnc002.mcc262.3gppnetwork.org")
            .setVisitedPlmnId(VISITED_PLMN);
        when(locationLteDao.getLocation(IMSI)).thenReturn(Optional.of(location));
        when(accessPolicy.isEutranAccessAllowed(IMSI, VISITED_MCC)).thenReturn(Optional.of(false));

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
        when(locationLteDao.getLocation(IMSI)).thenReturn(Optional.empty());

        underTest.receiveSimDeactivated(new SimDeactivated("iccid", MSISDN, List.of(IMSI)));

        verify(diameterSessions, never()).send(any());
        verify(locationLteDao, never()).removeExistingMmeLocation(any());
        verifyNoInteractions(accessPolicy);
    }

    @Test
    void itSendsNothingAndKeepsTheLocationWhenTheAccessPolicyAllowsTheImsi() {
        // GIVEN — the deactivated SIM still has an MME location, but access is allowed
        final var location = new LocationLTE()
            .setMmeHostname("mme.example.org")
            .setMmeRealm("epc.mnc002.mcc262.3gppnetwork.org")
            .setVisitedPlmnId(VISITED_PLMN);
        when(locationLteDao.getLocation(IMSI)).thenReturn(Optional.of(location));
        when(accessPolicy.isEutranAccessAllowed(IMSI, VISITED_MCC)).thenReturn(Optional.of(true));

        // WHEN
        underTest.receiveSimDeactivated(new SimDeactivated("iccid", MSISDN, List.of(IMSI)));

        // THEN — no CLR and no removal, the attach stays as it is; the policy decided for the
        // MCC of the visited PLMN stored with the location
        verify(diameterSessions, never()).send(any());
        verify(locationLteDao, never()).removeExistingMmeLocation(any());
    }

    @Test
    void itDetachesTheImsiWhenTheAccessPolicyCannotDecide() {
        // GIVEN — no decision available counts as denied: the SIM is deactivated, so the
        // attach must not outlive the subscription on an inconclusive policy answer
        doReturn(CompletableFuture.completedFuture(null)).when(diameterSessions).send(any());
        final var location = new LocationLTE()
            .setMmeHostname("mme.example.org")
            .setMmeRealm("epc.mnc002.mcc262.3gppnetwork.org")
            .setVisitedPlmnId(VISITED_PLMN);
        when(locationLteDao.getLocation(IMSI)).thenReturn(Optional.of(location));
        when(accessPolicy.isEutranAccessAllowed(IMSI, VISITED_MCC)).thenReturn(Optional.empty());

        underTest.receiveSimDeactivated(new SimDeactivated("iccid", MSISDN, List.of(IMSI)));

        verify(diameterSessions, times(1)).send(any());
        verify(locationLteDao).removeExistingMmeLocation(location);
    }

    @Test
    void itDetachesOnlyTheSingleImsiTheAccessPolicyDenies() {
        // GIVEN — one of two IMSIs is denied; both have stored MME locations
        doReturn(CompletableFuture.completedFuture(null)).when(diameterSessions).send(any());
        when(locationLteDao.getLocation(IMSI)).thenReturn(Optional.of(
            new LocationLTE().setMmeHostname("mme.example.org").setMmeRealm("epc.example.org").setVisitedPlmnId(VISITED_PLMN)));
        when(locationLteDao.getLocation(SECOND_IMSI)).thenReturn(Optional.of(
            new LocationLTE().setMmeHostname("mme2.example.org").setMmeRealm("epc.example.org").setVisitedPlmnId(VISITED_PLMN)));
        when(accessPolicy.isEutranAccessAllowed(IMSI, VISITED_MCC)).thenReturn(Optional.of(true));
        when(accessPolicy.isEutranAccessAllowed(SECOND_IMSI, VISITED_MCC)).thenReturn(Optional.of(false));

        // WHEN
        underTest.receiveSimDeactivated(new SimDeactivated("iccid", MSISDN, List.of(IMSI, SECOND_IMSI)));

        // THEN — exactly one CLR, for the denied IMSI only; the allowed one keeps its location
        final var captor = ArgumentCaptor.forClass(CancelLocationRequest.Out.class);
        verify(diameterSessions, times(1)).send(captor.capture());
        assertThat(captor.getValue().getUserName()).isEqualTo(SECOND_IMSI);
        assertThat(captor.getValue().getDestinationHost()).isEqualTo("mme2.example.org");
        verify(locationLteDao, times(1)).removeExistingMmeLocation(any());
    }

    @Test
    void itDetachesEveryImsiWhenTheAccessPolicyDeniesAllOfThem() {
        // GIVEN — both IMSIs are denied, each with its own MME location
        doReturn(CompletableFuture.completedFuture(null)).when(diameterSessions).send(any());
        final var firstLocation = new LocationLTE()
            .setMmeHostname("mme.example.org").setMmeRealm("epc.example.org").setVisitedPlmnId(VISITED_PLMN);
        final var secondLocation = new LocationLTE()
            .setMmeHostname("mme2.example.org").setMmeRealm("epc.example.org").setVisitedPlmnId(VISITED_PLMN);
        when(locationLteDao.getLocation(IMSI)).thenReturn(Optional.of(firstLocation));
        when(locationLteDao.getLocation(SECOND_IMSI)).thenReturn(Optional.of(secondLocation));
        when(accessPolicy.isEutranAccessAllowed(IMSI, VISITED_MCC)).thenReturn(Optional.of(false));
        when(accessPolicy.isEutranAccessAllowed(SECOND_IMSI, VISITED_MCC)).thenReturn(Optional.of(false));

        // WHEN
        underTest.receiveSimDeactivated(new SimDeactivated("iccid", MSISDN, List.of(IMSI, SECOND_IMSI)));

        // THEN — one CLR per denied IMSI, and both locations are removed
        final var captor = ArgumentCaptor.forClass(CancelLocationRequest.Out.class);
        verify(diameterSessions, times(2)).send(captor.capture());
        assertThat(captor.getAllValues()).extracting(CancelLocationRequest.Out::getUserName)
            .containsExactlyInAnyOrder(IMSI, SECOND_IMSI);
        verify(locationLteDao, times(2)).removeExistingMmeLocation(any());
    }

    @Test
    void itSwallowsAFailingTeardownAndStillDetachesTheRemainingImsis() {
        // GIVEN — reading the first IMSI's location fails (db error); the second IMSI is healthy.
        // The teardown runs synchronously in the publisher's transaction, so a propagating
        // exception would mark that transaction rollback-only and lose the sim deactivation —
        // it must be caught inside the listener instead.
        doReturn(CompletableFuture.completedFuture(null)).when(diameterSessions).send(any());
        when(locationLteDao.getLocation(IMSI)).thenThrow(new IllegalStateException("db down"));
        when(locationLteDao.getLocation(SECOND_IMSI)).thenReturn(Optional.of(
            new LocationLTE().setMmeHostname("mme2.example.org").setMmeRealm("epc.example.org").setVisitedPlmnId(VISITED_PLMN)));
        when(accessPolicy.isEutranAccessAllowed(SECOND_IMSI, VISITED_MCC)).thenReturn(Optional.of(false));

        // WHEN + THEN — nothing propagates and the healthy IMSI is still detached
        assertThatCode(() ->
            underTest.receiveSimDeactivated(new SimDeactivated("iccid", MSISDN, List.of(IMSI, SECOND_IMSI))))
            .doesNotThrowAnyException();
        final var captor = ArgumentCaptor.forClass(CancelLocationRequest.Out.class);
        verify(diameterSessions, times(1)).send(captor.capture());
        assertThat(captor.getValue().getUserName()).isEqualTo(SECOND_IMSI);
        verify(locationLteDao, times(1)).removeExistingMmeLocation(any());
    }

    @Test
    void itSwallowsAFailingSendOfACancelLocationEvent() {
        // GIVEN — the roaming-block CLR cannot be sent; the triggering inbound request (ULR)
        // must not fail because of it
        final var clr = diameterSessions.createRequest(
            CancelLocationRequest.Out.class, AUTH_SESSION_STATE_NOT_MAINTAINED,
            "mme.example.org", "epc.example.org", VENDOR_ID_3GPP, APP_ID_S6A_S6D);
        doThrow(new IllegalStateException("send failed")).when(diameterSessions).send(any());

        // WHEN + THEN
        assertThatCode(() -> underTest.receiveRequest(new CancelLocationEvent(clr, "roamingBlock")))
            .doesNotThrowAnyException();
        verify(diameterSessions, times(1)).send(any());
    }
}
