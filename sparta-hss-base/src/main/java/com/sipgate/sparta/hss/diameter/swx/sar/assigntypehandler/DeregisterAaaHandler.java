package com.sipgate.sparta.hss.diameter.swx.sar.assigntypehandler;

import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.EXP_RES_DIAMETER_ERROR_USER_UNKNOWN;
import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.VENDOR_ID_3GPP;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.EXP_RES_DIAMETER_ERROR_IDENTITY_ALREADY_REGISTERED;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.RES_DIAMETER_SUCCESS;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.RES_DIAMETER_UNABLE_TO_COMPLY;

import com.sipgate.sparta.diameter._3gpp.swx.messages.ServerAssignmentAnswer;
import com.sipgate.sparta.diameter._3gpp.swx.messages.ServerAssignmentRequest;
import com.sipgate.sparta.diameter.base.core.DiameterMessageFactory;
import com.sipgate.sparta.hss.diameter.swx.sar.AaaServerAssignmentChanged;
import com.sipgate.sparta.hss.event.EventPublisher;
import com.sipgate.sparta.hss.persistence.LocationVowifiDao;
import com.sipgate.sparta.hss.persistence.SimDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// SWx de-registration handler (3GPP TS 29.273 §8.1.2.1.3, slice 1). Handles the four
/// de-registration Server-Assignment-Types. The request-side AAA Server identity is carried in
/// Origin-Host (3GPP-AAA-Server-Name is answer-only), so the step-3 match compares the stored
/// AAA-Server name against `request.getOriginHost()`.
public final class DeregisterAaaHandler implements SwxServerAssignTypeHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeregisterAaaHandler.class);
    private static final int IMSI_LEN = 15;

    private final SimDao simDao;
    private final LocationVowifiDao locationVowifiDao;
    private final EventPublisher eventPublisher;

    public DeregisterAaaHandler(
        final SimDao simDao,
        final LocationVowifiDao locationVowifiDao,
        final EventPublisher eventPublisher)
    {
        this.simDao = simDao;
        this.locationVowifiDao = locationVowifiDao;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public ServerAssignmentAnswer.Out handle(final ServerAssignmentRequest.In request) {
        final var imsi = imsiFromRequest(request);

        if (simDao.getSimByImsiString(imsi).isEmpty()) {
            return SwxServerAssignTypeHandler.stampUserName(request,
                DiameterMessageFactory.createExperimentalResultAnswer(request, VENDOR_ID_3GPP, EXP_RES_DIAMETER_ERROR_USER_UNKNOWN));
        }

        final var stored = locationVowifiDao.getAaaServer(imsi);
        if (stored.isEmpty()) {
            LOGGER.info("imsi={}: No vowifi location found, unable to delete", imsi);
            return SwxServerAssignTypeHandler.stampUserName(request,
                DiameterMessageFactory.createAnswer(request, RES_DIAMETER_UNABLE_TO_COMPLY));
        }

        final var storedName = stored.get().getAaaServerName();
        if (!storedName.equals(request.getOriginHost())) {
            LOGGER.info("imsi={}: vowifi location does not match, unable to delete. stored server name={}, requested origin host={}", imsi, storedName, request.getOriginHost());
            final var answer = DiameterMessageFactory.createExperimentalResultAnswer(
                request, VENDOR_ID_3GPP, EXP_RES_DIAMETER_ERROR_IDENTITY_ALREADY_REGISTERED);
            // TODO: Leaked das keine Infos an unbekannte Requestors? Vielleicht "i couldnt tell" oder sowas antworten. Vielleicht aber erstmal schauen, wer das sendet und was darin steht.
            answer.set3gppAaaServerName(storedName);
            return SwxServerAssignTypeHandler.stampUserName(request, answer);
        }

        locationVowifiDao.clearAaaServer(imsi);
        eventPublisher.publish(AaaServerAssignmentChanged.ofUnregister(imsi));
        return SwxServerAssignTypeHandler.stampUserName(request,
            DiameterMessageFactory.createAnswer(request, RES_DIAMETER_SUCCESS));
    }

    private static String imsiFromRequest(final ServerAssignmentRequest.In request) {
        final var userName = request.getUserName();
        if (userName == null || userName.length() < IMSI_LEN) {
            return "";
        }
        return userName.substring(0, IMSI_LEN);
    }
}
