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
import com.sipgate.sparta.hss.service.subscription.SimDeactivated;
import jakarta.transaction.Transactional;
import java.util.List;

public class RegistrationTerminationOutboundListener extends BaseOutboundListener {

    private final ImsiScscfDao imsiScscfDao;
    private final LocationIpSmGwDao locationIpSmGwDao;

    public RegistrationTerminationOutboundListener(
        final DiameterSessions diameterSessions,
        final ImsiScscfDao imsiScscfDao,
        final LocationIpSmGwDao locationIpSmGwDao) {
        super(diameterSessions);
        this.imsiScscfDao = imsiScscfDao;
        this.locationIpSmGwDao = locationIpSmGwDao;
    }

    public void receiveRequest(final RegistrationTerminationEvent event) {
        onEvent(event);
    }

    /// For every IMSI of the deactivated SIM, removes the IP-SM-GW row (it belongs to the IMS
    /// domain, written by the Cx SAR handlers, and needs no signalling of its own) and, if an
    /// S-CSCF was stored in `imsi_scscf`, sends an RTR with Reason-Code `PERMANENT_TERMINATION`
    /// (TS 29.228 §6.1.3.1, equivalent to the one in
    /// [com.sipgate.sparta.hss.diameter.cx.mar.MultimediaAuthHandler] except for the Reason-Code;
    /// the Private Identity (User-Name) is the IMSI) and clears the assignment. The listener owns
    /// the `imsi_scscf` and `location_ip_sm_gw` lifecycle; the event carries no per-peer data —
    /// it is cleared here, not by the deactivating service, so a deployment without the Cx
    /// capability keeps the stale (harmless, 4G-barred) rows.
    @Transactional
    public void receiveSimDeactivated(final SimDeactivated event) {
        for (final var imsi : event.imsis()) {
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
