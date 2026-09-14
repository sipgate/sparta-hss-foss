package com.sipgate.sparta.hss.service.profile;

import com.sipgate.sparta.diameter._3gpp.common._3gppConstants;
import com.sipgate.sparta.diameter._3gpp.s6a.S6aConstants;
import com.sipgate.sparta.diameter._3gpp.s6a.messages.InsertSubscriberDataRequest;
import com.sipgate.sparta.diameter.base.core.DiameterConstants;
import com.sipgate.sparta.diameter.base.core.avp.AVP;
import com.sipgate.sparta.diameter.base.core.avp.AVPKey;
import com.sipgate.sparta.hss.diameter.client.DiameterSessions;
import com.sipgate.sparta.hss.diameter.s6a.common.SubscriptionDataFactory;
import com.sipgate.sparta.hss.diameter.s6a.isd.InsertSubscriberDataEvent;
import com.sipgate.sparta.hss.event.EventPublisher;
import com.sipgate.sparta.hss.persistence.EffectiveProfileDao.UpdateProfileResult;
import java.time.Duration;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProfileService {
    /// 1500 auf einmal waren in Ordnung. 5000 waren zu viel. Daher: Throttle.
    /// - 300ms => per min: 200; 20k brauchen 100min
    /// Update: war doch zu viel.
    /// - 500ms => per min: 120; 20k brauchen 170min
    static final int THROTTLE_NOTICE_COUNT = 400; // new log message after 400x500ms=200s
    static final Duration THROTTLE_WAIT = Duration.ofMillis(500);
    private static final Logger LOGGER = LoggerFactory.getLogger(ProfileService.class);
    private final SubscriptionDataFactory subscriptionDataFactory;
    private final EventPublisher eventPublisher;
    private final DiameterSessions diameterSessions;

    public ProfileService(
        final SubscriptionDataFactory subscriptionDataFactory,
        final EventPublisher eventPublisher, final DiameterSessions diameterSessions) {
        this.subscriptionDataFactory = subscriptionDataFactory;
        this.eventPublisher = eventPublisher;
        this.diameterSessions = diameterSessions;
    }

    // No transaction here: the method only publishes throttled ISD events. Callers that update
    // the database (BatchProfileService) own the transaction.
    public void updateProfiles(final Collection<UpdateProfileResult> effectiveImsiProfiles)
            throws InterruptedException {
        var count = 0;
        for (final var effectiveImsiProfile : effectiveImsiProfiles) {
            sendInsertSubscriberDataRequest(effectiveImsiProfile);
            if (count % THROTTLE_NOTICE_COUNT == 0) {
                LOGGER.info("Throttling active, still to go: {}", effectiveImsiProfiles.size() - count);
            }
            count += 1;
            Thread.sleep(THROTTLE_WAIT.toMillis());
        }

        LOGGER.info("updated {} profiles", effectiveImsiProfiles.size());
    }

    private void sendInsertSubscriberDataRequest(final UpdateProfileResult profileUpdate) {
        final var subscriptionData = subscriptionDataFactory.createSubscriptionData(
                profileUpdate.getVisitedPlmnId(), profileUpdate.getProfile(), profileUpdate.getMsisdn());

        final var request = diameterSessions.createRequest(
            InsertSubscriberDataRequest.Out.class,
            DiameterConstants.AUTH_SESSION_STATE_NOT_MAINTAINED,
            profileUpdate.getMmeHostname(),
            profileUpdate.getMmeRealm(), _3gppConstants.VENDOR_ID_3GPP, S6aConstants.APP_ID_S6A_S6D
        );

        request.setUserName(profileUpdate.getImsi());

        request.setAVP(AVP.create(new AVPKey(S6aConstants.AVP_SUBSCRIPTION_DATA, _3gppConstants.VENDOR_ID_3GPP), subscriptionData));

        // Hand the request to the outbound listener so it is sent off the throttle loop's thread.
        eventPublisher.publish(new InsertSubscriberDataEvent(
            request, "imsi=" + profileUpdate.getImsi() + ", mme=" + profileUpdate.getMmeHostname()));
    }
}
