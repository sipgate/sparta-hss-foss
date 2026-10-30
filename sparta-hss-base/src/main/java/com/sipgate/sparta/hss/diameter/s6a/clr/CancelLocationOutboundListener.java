package com.sipgate.sparta.hss.diameter.s6a.clr;

import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.VENDOR_ID_3GPP;
import static com.sipgate.sparta.diameter._3gpp.s6a.S6aConstants.APP_ID_S6A_S6D;
import static com.sipgate.sparta.diameter._3gpp.s6a.S6aConstants.CANCELLATION_TYPE_SUBSCRIPTION_WITHDRAWAL;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.AUTH_SESSION_STATE_NOT_MAINTAINED;

import com.sipgate.sparta.diameter._3gpp.s6a.messages.CancelLocationRequest;
import com.sipgate.sparta.hss.diameter.client.DiameterSessions;
import com.sipgate.sparta.hss.diameter.common.BaseOutboundListener;
import com.sipgate.sparta.hss.diameter.common.auth.MncMccFormatter;
import com.sipgate.sparta.hss.persistence.LocationLteDao;
import com.sipgate.sparta.hss.service.accessPolicy.AccessPolicy;
import com.sipgate.sparta.hss.service.subscription.SimDeactivated;
import jakarta.transaction.Transactional;

public class CancelLocationOutboundListener extends BaseOutboundListener {

    private final LocationLteDao locationLteDao;
    private final AccessPolicy accessPolicy;

    public CancelLocationOutboundListener(
        final DiameterSessions diameterSessions,
        final LocationLteDao locationLteDao, final AccessPolicy accessPolicy) {
        super(diameterSessions);
        this.locationLteDao = locationLteDao;
        this.accessPolicy = accessPolicy;
    }

    public void receiveRequest(final CancelLocationEvent event) {
        onEvent(event);
    }

    /// Checks if the configured `accessPolicy` currently allows the given IMSIs in their visiting
    /// MCC and then detaches the IMSIs of the deactivated SIM from their stored MME. A barred SIM
    /// must be cut off immediately, not at the next attach: CLR with Cancellation-Type
    /// `SUBSCRIPTION_WITHDRAWAL` without the re-attach flag (TS 23.401 §5.3.8.4), then the row
    /// is removed.
    @Transactional
    public void receiveSimDeactivated(final SimDeactivated event) {
        for (final var imsi : event.imsis()) {
            final var maybeLocation = locationLteDao.getLocation(imsi);
            if (maybeLocation.isEmpty()) {
                continue;
            }
            final var location = maybeLocation.get();

            final var mcc = MncMccFormatter.plmnToMcc(location.getVisitedPlmnId());
            if (accessPolicy.isEutranAccessAllowed(imsi, mcc).orElse(false)) {
                continue;
            }

            final var clr = getDiameterSessions().createRequest(
                CancelLocationRequest.Out.class,
                AUTH_SESSION_STATE_NOT_MAINTAINED,
                location.getMmeHostname(),
                location.getMmeRealm(),
                VENDOR_ID_3GPP,
                APP_ID_S6A_S6D);
            clr.setUserName(imsi);
            // CLR-Flags bit 0 = S6a/S6d-Indicator (TS 29.272 §7.3.152): the old serving node is an MME.
            clr.setClrFlags(1L);
            clr.setCancellationType(CANCELLATION_TYPE_SUBSCRIPTION_WITHDRAWAL);

            send(clr, "imsi=" + imsi + ", mme=" + location.getMmeHostname() + ", simDeactivation");
            locationLteDao.removeExistingMmeLocation(location);
        }
    }
}
