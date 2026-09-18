package com.sipgate.sparta.hss.diameter.cx.sar.assigntypehandler;

import com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants;
import com.sipgate.sparta.diameter._3gpp.cxdx.messages.ServerAssignmentAnswer;
import com.sipgate.sparta.diameter._3gpp.cxdx.messages.ServerAssignmentRequest;
import com.sipgate.sparta.diameter.base.core.DiameterConstants;
import com.sipgate.sparta.diameter.base.core.DiameterMessageFactory;
import com.sipgate.sparta.diameter.base.session.DiameterErrorAnswerException;
import com.sipgate.sparta.hss.diameter.common.ServingNodeInfo;
import com.sipgate.sparta.hss.diameter.cx.sar.identity.PublicIdentity;
import com.sipgate.sparta.hss.diameter.cx.sar.service.ImsService;
import com.sipgate.sparta.hss.diameter.cx.sar.ScscfAssignmentChanged;
import com.sipgate.sparta.hss.event.EventPublisher;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RegisterHandler extends ServerAssignTypeHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegisterHandler.class);
    private final ImsService imsService;
    private final EventPublisher eventPublisher;

    public RegisterHandler(final ImsService imsService, final EventPublisher eventPublisher) {
        this.imsService = imsService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public ServerAssignmentAnswer.Out handle(final ServerAssignmentRequest.In request, final PublicIdentity identity) throws DiameterErrorAnswerException {
        final var imsi = getImsiFromRequest(request);
        final var maybeRegisteredScscf = imsService.getScscf(imsi);
        final var scscf = request.getServerName();

        final var answer = DiameterMessageFactory.createAnswer(request, DiameterConstants.RES_DIAMETER_SUCCESS);

        if (maybeRegisteredScscf.isPresent() && !Objects.equals(maybeRegisteredScscf.get(), scscf)) {
            LOGGER.warn("Identity {} is already registered with a different scscf: {}. Allowing anyway.", imsi, maybeRegisteredScscf.get());
        }

        imsService.setScscf(imsi, scscf, request.getOriginHost(), request.getOriginRealm());

        // absent AVP (getter returns -1) defaults to USER_DATA_NOT_AVAILABLE per TS 29.229 §6.3.26
        final var userDataAlreadyAvailable = request.getUserDataAlreadyAvailable();
        if (userDataAlreadyAvailable != CxDxConstants.USER_DATA_ALREADY_AVAILABLE) {
            final var userData = imsService.createUserData(request.getUserName(), identity.getMsisdn());
            answer.setUserData(userData.getBytes());
        }

        // handling of SMS over IP: if Serving-Node AVPs are present, store them in DB.
        final var servingNode = ServingNodeInfo.from(request);
        if (servingNode != null) {
            imsService.setIpSmGw(imsi, servingNode.ipSmGwName(), servingNode.ipSmGwRealm());
        }

        eventPublisher.publish(ScscfAssignmentChanged.ofRegister(imsi, scscf));

        // 3GPP mandates only the loose routing for IMS
        answer.setLooseRouteIndication(1);
        return answer;
    }
}
