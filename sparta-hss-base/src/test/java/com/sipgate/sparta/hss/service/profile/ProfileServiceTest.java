package com.sipgate.sparta.hss.service.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sipgate.sparta.diameter._3gpp.s6a.messages.InsertSubscriberDataRequest;
import com.sipgate.sparta.diameter.base.core.DiameterConstants;
import com.sipgate.sparta.hss.diameter.client.DiameterSessions;
import com.sipgate.sparta.hss.diameter.common.Factory;
import com.sipgate.sparta.hss.diameter.s6a.common.SubscriptionDataFactory;
import com.sipgate.sparta.hss.diameter.s6a.isd.InsertSubscriberDataEvent;
import com.sipgate.sparta.hss.persistence.EffectiveProfileDao.UpdateProfileResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.sipgate.sparta.hss.event.EventPublisher;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    private static final String IMSI = "262031234567890";

    private ProfileService underTest;

    @Mock
    private SubscriptionDataFactory subscriptionDataFactory;

    @Mock
    private EventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        final var diameterSessions = new DiameterSessions(Factory.nodeConfig("origin-host", "origin-realm"));
        underTest = new ProfileService(subscriptionDataFactory, eventPublisher, diameterSessions);
    }

    @Test
    void itPublishesInsertSubscriberDataWithAuthSessionState() throws InterruptedException {
        // GIVEN
        when(subscriptionDataFactory.createSubscriptionData("vplmnid", "default", "msisdn"))
            .thenReturn(List.of());

        // WHEN
        underTest.updateProfiles(List.of(new UpdateProfileResult(
            IMSI, "mmeHost", "mmeRealm", "msisdn", "default", "vplmnid")));

        // THEN
        final var eventCaptor = ArgumentCaptor.forClass(InsertSubscriberDataEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());

        // the Session-Id is stamped by the outbound listener, not the producer, so it is unset here
        final var request = (InsertSubscriberDataRequest.Out) eventCaptor.getValue().getRequest();
        assertThat(request.getAuthSessionState()).isEqualTo(DiameterConstants.AUTH_SESSION_STATE_NOT_MAINTAINED);
        assertThat(request.getUserName()).isEqualTo(IMSI);
        assertThat(request.getDestinationHost()).isEqualTo("mmeHost");
        assertThat(request.getDestinationRealm()).isEqualTo("mmeRealm");
    }
}
