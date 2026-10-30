package com.sipgate.sparta.hss.diameter.swx.rtr;

import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.VENDOR_ID_3GPP;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.AVP_REASON_CODE;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.AVP_REASON_INFO;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.REASON_CODE_PERMANENT_TERMINATION;
import static com.sipgate.sparta.diameter._3gpp.swx.SwxConstants.APP_ID_SWX;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.AUTH_SESSION_STATE_NOT_MAINTAINED;

import com.sipgate.sparta.diameter._3gpp.swx.messages.RegistrationTerminationRequest;
import com.sipgate.sparta.diameter.base.core.avp.AVP;
import com.sipgate.sparta.diameter.base.core.avp.AVPKey;
import com.sipgate.sparta.hss.diameter.client.DiameterSessions;
import com.sipgate.sparta.hss.diameter.common.BaseOutboundListener;
import com.sipgate.sparta.hss.persistence.LocationVowifiDao;
import com.sipgate.sparta.hss.service.accessPolicy.AccessPolicy;
import com.sipgate.sparta.hss.service.subscription.SimDeactivated;
import jakarta.transaction.Transactional;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Sends the SWx RTR on SIM deactivation.
public class SwxRegistrationTerminationOutboundListener extends BaseOutboundListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(SwxRegistrationTerminationOutboundListener.class);

    private final LocationVowifiDao locationVowifiDao;
    private final AccessPolicy accessPolicy;

    public SwxRegistrationTerminationOutboundListener(
        final DiameterSessions diameterSessions,
        final LocationVowifiDao locationVowifiDao, final AccessPolicy accessPolicy) {
        super(diameterSessions);
        this.locationVowifiDao = locationVowifiDao;
        this.accessPolicy = accessPolicy;
    }

    /// Ends the SWx registration of the deactivated SIM's IMSIs if the configured `accessPolicy`
    /// denies IMS access for the SIM's IMSIs.
    /// Send an RTR with Reason-Code `PERMANENT_TERMINATION` (TS 29.273 §8.1.2.2.3) to the AAA,
    /// then the assignment is cleared in the HSS data store.
    ///
    /// Runs synchronously in the publisher's transaction; a failing teardown is therefore caught
    /// here — inside the transactional boundary — so it can neither mark the publisher's
    /// transaction rollback-only (the SIM deactivation must commit regardless) nor stop the
    /// remaining IMSIs of the same SIM. The teardown is best-effort by design.
    @Transactional
    public void receiveSimDeactivated(final SimDeactivated event) {
        for (final var imsi : event.imsis()) {
            try {
                if (accessPolicy.isImsAccessAllowed(imsi).orElse(false)) {
                    continue;
                }

                final var aaaServer = locationVowifiDao.getAaaServer(imsi);
                if (aaaServer.isEmpty()) {
                    continue;
                }

                final var assignment = aaaServer.get();
                final var rtr = getDiameterSessions().createRequest(
                    RegistrationTerminationRequest.Out.class,
                    AUTH_SESSION_STATE_NOT_MAINTAINED,
                    assignment.getDiameterHost(),
                    assignment.getDiameterRealm(),
                    VENDOR_ID_3GPP,
                    APP_ID_SWX);
                rtr.setUserName(imsi);
                rtr.setDeregistrationReason(List.of(
                    AVP.create(new AVPKey(AVP_REASON_CODE, VENDOR_ID_3GPP), REASON_CODE_PERMANENT_TERMINATION),
                    AVP.create(new AVPKey(AVP_REASON_INFO, VENDOR_ID_3GPP), "subscription terminated")));

                send(rtr, "imsi=" + imsi + ", aaaServer=" + assignment.getDiameterHost() + ", simDeactivation");
                locationVowifiDao.clearAaaServer(imsi);
            } catch (final Exception e) {
                LOGGER.error("failed to end the SWx registration of imsi {} on sim deactivation", imsi, e);
            }
        }
    }
}
