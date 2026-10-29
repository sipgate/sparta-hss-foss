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
import com.sipgate.sparta.hss.service.subscription.SimDeactivated;
import jakarta.transaction.Transactional;
import java.util.List;

/// Sends the SWx RTR on SIM deactivation.
public class SwxRegistrationTerminationOutboundListener extends BaseOutboundListener {

    private final LocationVowifiDao locationVowifiDao;

    public SwxRegistrationTerminationOutboundListener(
        final DiameterSessions diameterSessions,
        final LocationVowifiDao locationVowifiDao) {
        super(diameterSessions);
        this.locationVowifiDao = locationVowifiDao;
    }

    /// Ends the SWx registration of the deactivated SIM's IMSIs, so the VoWiFi attach does not
    /// outlive the subscription: RTR with Reason-Code `PERMANENT_TERMINATION`
    /// (TS 29.273 §8.1.2.2.3), then the assignment is cleared.
    @Transactional
    public void receiveSimDeactivated(final SimDeactivated event) {
        for (final var imsi : event.imsis()) {
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
        }
    }
}
