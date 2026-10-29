package com.sipgate.sparta.hss.service.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sipgate.sparta.hss.event.EventPublisher;
import com.sipgate.sparta.hss.persistence.SimDao;
import com.sipgate.sparta.hss.persistence.entities.Msisdn;
import com.sipgate.sparta.hss.persistence.entities.Sim;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// The service flips the subscription switch and publishes one identity-only [SimDeactivated];
/// the registration teardown itself lives in the outbound listeners, so these tests only pin the
/// switch and the event payload.
@ExtendWith(MockitoExtension.class)
class SubscriptionStateServiceTest {

    private static final String ICCID = "8988173000000123456";
    private static final String IMSI = "262021234567890";
    private static final String SECOND_IMSI = "262029876543210";
    private static final String MSISDN = "4912345678901";

    @Mock
    private SimDao simDao;

    @Mock
    private EventPublisher eventPublisher;

    private SubscriptionStateService underTest;

    @BeforeEach
    void setUp() {
        underTest = new SubscriptionStateService(simDao, eventPublisher);
    }

    @Test
    void deactivate_sets_sim_inactive_and_publishes_the_identity() {
        final var sim = activeSim();
        when(simDao.getSimByIccid(ICCID)).thenReturn(Optional.of(sim));

        underTest.deactivate(ICCID);

        assertThat(sim.isActive()).isFalse();
        final var captor = ArgumentCaptor.forClass(SimDeactivated.class);
        verify(eventPublisher).publish(captor.capture());
        final var event = captor.getValue();
        assertThat(event.iccid()).isEqualTo(ICCID);
        assertThat(event.msisdn()).isEqualTo(MSISDN);
        assertThat(event.imsis()).containsExactlyInAnyOrder(IMSI, SECOND_IMSI);
    }

    @Test
    void deactivate_fails_when_iccid_unknown_and_publishes_nothing() {
        when(simDao.getSimByIccid(ICCID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> underTest.deactivate(ICCID)).isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void repeated_deactivation_publishes_the_event_again() {
        // idempotent: the switch stays off, the listeners send only what their tables still hold
        final var sim = activeSim();
        sim.setActive(false);
        when(simDao.getSimByIccid(ICCID)).thenReturn(Optional.of(sim));

        underTest.deactivate(ICCID);

        assertThat(sim.isActive()).isFalse();
        verify(eventPublisher).publish(org.mockito.ArgumentMatchers.any(SimDeactivated.class));
    }

    @Test
    void activate_only_flips_the_switch() {
        final var sim = activeSim();
        sim.setActive(false);
        when(simDao.getSimByIccid(ICCID)).thenReturn(Optional.of(sim));

        underTest.activate(ICCID);

        assertThat(sim.isActive()).isTrue();
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void activate_fails_when_iccid_unknown() {
        when(simDao.getSimByIccid(ICCID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> underTest.activate(ICCID)).isInstanceOf(NoSuchElementException.class);
    }

    private Sim activeSim() {
        final var msisdn = new Msisdn();
        msisdn.setMsisdn(MSISDN);
        final var sim = new Sim(msisdn, IMSI, new byte[] {1}, null);
        sim.setIccid(ICCID);
        final var secondImsi = new com.sipgate.sparta.hss.persistence.entities.Imsi();
        secondImsi.setImsi(SECOND_IMSI);
        sim.getImsis().add(secondImsi);
        return sim;
    }
}
