package com.sipgate.sparta.hss.diameter.cx.sar.assigntypehandler;

import com.sipgate.sparta.diameter._3gpp.cxdx.messages.ServerAssignmentAnswer;
import com.sipgate.sparta.diameter._3gpp.cxdx.messages.ServerAssignmentRequest;
import com.sipgate.sparta.diameter.base.session.DiameterErrorAnswerException;
import com.sipgate.sparta.hss.diameter.cx.sar.identity.PublicIdentity;
import com.sipgate.sparta.hss.diameter.cx.sar.service.ImsService;
import com.sipgate.sparta.hss.diameter.cx.sar.ScscfAssignmentChanged;
import com.sipgate.sparta.hss.event.EventPublisher;

public class DeregistrationHandler extends ServerAssignTypeHandler {

    private final ImsService imsService;
    private final EventPublisher eventPublisher;
    private final SaaOutcome outcome;

    public DeregistrationHandler(final ImsService imsService, final SaaOutcome outcome, final EventPublisher eventPublisher) {
        this.imsService = imsService;
        this.outcome = outcome;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public ServerAssignmentAnswer.Out handle(final ServerAssignmentRequest.In request, final PublicIdentity identity)
        throws DiameterErrorAnswerException {
        final var imsi = getImsiFromRequest(request);
        final var maybeExistingScscf = imsService.getScscf(imsi);
        // Some S-CSCFs send a de-registration request from an S-CSCF that is not the current one,
        // so only clear the assignment if it actually matches.
        if (maybeExistingScscf.isPresent() && maybeExistingScscf.get().equals(request.getServerName())) {
            imsService.clearScscf(imsi);
            imsService.clearIpSmGw(imsi);
        }

        eventPublisher.publish(ScscfAssignmentChanged.ofUnregister(imsi));

        return outcome.createAnswer(request);
    }
}
