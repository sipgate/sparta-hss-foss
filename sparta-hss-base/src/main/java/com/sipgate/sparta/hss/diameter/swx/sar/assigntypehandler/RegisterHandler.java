package com.sipgate.sparta.hss.diameter.swx.sar.assigntypehandler;

import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.EXP_RES_DIAMETER_ERROR_USER_UNKNOWN;
import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.VENDOR_ID_3GPP;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.EXP_RES_DIAMETER_ERROR_IDENTITY_ALREADY_REGISTERED;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.RES_DIAMETER_SUCCESS;

import com.sipgate.sparta.diameter._3gpp.swx.messages.ServerAssignmentAnswer;
import com.sipgate.sparta.diameter._3gpp.swx.messages.ServerAssignmentRequest;
import com.sipgate.sparta.diameter.base.core.DiameterMessageFactory;
import com.sipgate.sparta.hss.diameter.swx.sar.Non3gppUserDataFactory;
import com.sipgate.sparta.hss.diameter.swx.sar.AaaServerAssignmentChanged;
import com.sipgate.sparta.hss.event.EventPublisher;
import com.sipgate.sparta.hss.persistence.ImsiProfileDao;
import com.sipgate.sparta.hss.persistence.LocationVowifiDao;
import com.sipgate.sparta.hss.persistence.SimDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// SWx REGISTRATION / RE_REGISTRATION handler (3GPP TS 29.273 §8.1.2.2.2). On a known SIM whose
/// stored AAA-Server matches the requester (or none is stored yet), answers DIAMETER_SUCCESS with the
/// Non-3GPP-User-Data subscription profile. Mirrors DeregisterAaaHandler's lookup/conflict shape; SAR
/// has no AAA-Failure-Indication, so a conflict has no override (-> 5005 + the stored old name).
public final class RegisterHandler implements SwxServerAssignTypeHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegisterHandler.class);
    private static final int IMSI_LEN = 15;
    private static final String DEFAULT_PROFILE = "default";

    /// TS 29.273 §10.3 USER_NO_NON_3GPP_SUBSCRIPTION. HSS-side until the entitlement gate is real;
    /// may move to SwxConstants then. (The Python test-double currently carries 5650 — a pre-existing
    /// divergence; this is the spec value.)
    private static final long EXP_RES_DIAMETER_ERROR_USER_NO_NON_3GPP_SUBSCRIPTION = 5450L;

    private final SimDao simDao;
    private final LocationVowifiDao locationVowifiDao;
    private final ImsiProfileDao imsiProfileDao;
    private final Non3gppUserDataFactory non3gppUserDataFactory;
    private final EventPublisher eventPublisher;

    public RegisterHandler(
        final SimDao simDao,
        final LocationVowifiDao locationVowifiDao,
        final ImsiProfileDao imsiProfileDao,
        final Non3gppUserDataFactory non3gppUserDataFactory,
        final EventPublisher eventPublisher)
    {
        this.simDao = simDao;
        this.locationVowifiDao = locationVowifiDao;
        this.imsiProfileDao = imsiProfileDao;
        this.non3gppUserDataFactory = non3gppUserDataFactory;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public ServerAssignmentAnswer.Out handle(final ServerAssignmentRequest.In request) {
        final var imsi = imsiFromRequest(request);
        final var originHost = request.getOriginHost();

        if (simDao.getSimByImsiString(imsi).isEmpty()) {
            return SwxServerAssignTypeHandler.stampUserName(request, DiameterMessageFactory
                .createExperimentalResultAnswer(request, VENDOR_ID_3GPP, EXP_RES_DIAMETER_ERROR_USER_UNKNOWN));
        }

        // PLACEHOLDER ENTITLEMENT SEAM — the real per-SIM non-3GPP gate plugs in here. Today every known
        // SIM is entitled (hasNon3gppSubscription returns true), so the 5450 branch is dormant and performs
        // NO state mutation. When the real gate lands it should ALSO clear the stored AAA name + publish
        // ofUnregister per §8.1.2.2.2.2 step-3 — deliberately omitted now so the wired-but-dead branch is safe.
        if (!hasNon3gppSubscription(imsi)) {
            LOGGER.info("imsi={} no non-3GPP subscription — USER_NO_NON_3GPP_SUBSCRIPTION (dormant seam)", imsi);
            return SwxServerAssignTypeHandler.stampUserName(request, DiameterMessageFactory
                .createExperimentalResultAnswer(request, VENDOR_ID_3GPP,
                    EXP_RES_DIAMETER_ERROR_USER_NO_NON_3GPP_SUBSCRIPTION));
        }

        final var stored = locationVowifiDao.getAaaServer(imsi);
        if (stored.isPresent() && !stored.get().getAaaServerName().equals(originHost)) {
            final var storedName = stored.get().getAaaServerName();
            LOGGER.info("imsi={} REGISTRATION conflict: stored={} request={} — IDENTITY_ALREADY_REGISTERED",
                imsi, storedName, originHost);
            final var answer = DiameterMessageFactory.createExperimentalResultAnswer(
                request, VENDOR_ID_3GPP, EXP_RES_DIAMETER_ERROR_IDENTITY_ALREADY_REGISTERED);
            answer.set3gppAaaServerName(storedName);
            return SwxServerAssignTypeHandler.stampUserName(request, answer);
        }

        // Establish the assignment only when previously absent (mirror MAR step 8); same-name re-reg is idempotent.
        if (stored.isEmpty()) {
            LOGGER.info("imsi={} REGISTRATION establishing AAA-Server assignment to {}", imsi, originHost);
            locationVowifiDao.store(imsi, originHost, originHost, request.getOriginRealm());
            eventPublisher.publish(AaaServerAssignmentChanged.ofRegister(imsi, originHost));
        }

        final var profileName = imsiProfileDao.findByImsi(imsi).orElse(DEFAULT_PROFILE);
        final var answer = DiameterMessageFactory.createAnswer(request, RES_DIAMETER_SUCCESS);
        answer.setNon3gppUserData(non3gppUserDataFactory.createNon3gppUserData(profileName, null));
        LOGGER.info("imsi={} REGISTRATION success, Non-3GPP-User-Data from profile={}", imsi, profileName);
        return SwxServerAssignTypeHandler.stampUserName(request, answer);
    }

    /// PLACEHOLDER: real non-3GPP entitlement lookup goes here (return false -> 5450). Today every known
    /// SIM is entitled, so this always returns true and the 5450 path stays dormant.
    private boolean hasNon3gppSubscription(final String imsi) {
        return true;
    }

    private static String imsiFromRequest(final ServerAssignmentRequest.In request) {
        final var userName = request.getUserName();
        if (userName == null || userName.length() < IMSI_LEN) {
            return "";
        }
        return userName.substring(0, IMSI_LEN);
    }
}
