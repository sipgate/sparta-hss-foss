package com.sipgate.sparta.hss.diameter.cx.rtr;

import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.VENDOR_ID_3GPP;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.APP_ID_CX_DX;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.AVP_REASON_CODE;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.AVP_REASON_INFO;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.REASON_CODE_PERMANENT_TERMINATION;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.AUTH_SESSION_STATE_NOT_MAINTAINED;

import com.sipgate.sparta.diameter._3gpp.cxdx.messages.RegistrationTerminationRequest;
import com.sipgate.sparta.diameter.base.core.avp.AVP;
import com.sipgate.sparta.diameter.base.core.avp.AVPKey;
import com.sipgate.sparta.hss.diameter.client.DiameterSessions;
import com.sipgate.sparta.hss.diameter.common.BaseOutboundListener;
import com.sipgate.sparta.hss.persistence.ImsiScscfDao;
import com.sipgate.sparta.hss.persistence.LocationIpSmGwDao;
import com.sipgate.sparta.hss.service.accessPolicy.AccessPolicy;
import com.sipgate.sparta.hss.service.subscription.SimDeactivated;
import jakarta.transaction.Transactional;
import java.util.List;

public class RegistrationTerminationOutboundListener extends BaseOutboundListener {

    private final ImsiScscfDao imsiScscfDao;
    private final LocationIpSmGwDao locationIpSmGwDao;
    private final AccessPolicy accessPolicy;

    public RegistrationTerminationOutboundListener(
        final DiameterSessions diameterSessions,
        final ImsiScscfDao imsiScscfDao,
        final LocationIpSmGwDao locationIpSmGwDao, final AccessPolicy accessPolicy) {
        super(diameterSessions);
        this.imsiScscfDao = imsiScscfDao;
        this.locationIpSmGwDao = locationIpSmGwDao;
        this.accessPolicy = accessPolicy;
    }

    public void receiveRequest(final RegistrationTerminationEvent event) {
        onEvent(event);
    }

    /// De-registers the IMSIs of the deactivated SIM from their stored S-CSCF, if the configured
    /// `accessPolicy` currently denies IMS access for this IMSI. The IMS registration ends for
    /// any active subscription: RTR with Reason-Code `PERMANENT_TERMINATION` (TS 29.228 §6.1.3.1),
    /// then the assignment is cleared. The IP-SM-GW row belongs to the same IMS registration and
    /// is removed without signalling.
    @Transactional
    public void receiveSimDeactivated(final SimDeactivated event) {
        for (final var imsi : event.imsis()) {
            if (accessPolicy.isImsAccessAllowed(imsi).orElse(false)) {
                continue;
            }

            locationIpSmGwDao.clearIpSmGw(imsi);

            final var scscf = imsiScscfDao.getScscf(imsi);
            if (scscf.isEmpty()) {
                continue;
            }

            final var assignment = scscf.get();
            final var rtr = getDiameterSessions().createRequest(
                RegistrationTerminationRequest.Out.class,
                AUTH_SESSION_STATE_NOT_MAINTAINED,
                assignment.getDiameterHost(),
                assignment.getDiameterRealm(),
                VENDOR_ID_3GPP,
                APP_ID_CX_DX);
            rtr.setUserName(imsi);
            rtr.setDeregistrationReason(List.of(
                AVP.create(new AVPKey(AVP_REASON_CODE, VENDOR_ID_3GPP), REASON_CODE_PERMANENT_TERMINATION),
                AVP.create(new AVPKey(AVP_REASON_INFO, VENDOR_ID_3GPP), "subscription terminated")));
            rtr.addPublicIdentity("tel:+" + event.msisdn());

            send(rtr, "imsi=" + imsi + ", scscf=" + assignment.getDiameterHost() + ", simDeactivation");
            imsiScscfDao.clearScscf(imsi);
        }
    }
}
