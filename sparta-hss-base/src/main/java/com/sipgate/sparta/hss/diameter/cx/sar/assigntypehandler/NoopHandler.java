package com.sipgate.sparta.hss.diameter.cx.sar.assigntypehandler;

import com.sipgate.sparta.diameter._3gpp.cxdx.messages.ServerAssignmentAnswer;
import com.sipgate.sparta.diameter._3gpp.cxdx.messages.ServerAssignmentRequest;
import com.sipgate.sparta.diameter.base.session.DiameterErrorAnswerException;
import com.sipgate.sparta.hss.diameter.cx.sar.identity.PublicIdentity;

public class NoopHandler extends ServerAssignTypeHandler {

    private final SaaOutcome outcome;

    public NoopHandler(final SaaOutcome outcome) {
        this.outcome = outcome;
    }

    @Override
    public ServerAssignmentAnswer.Out handle(final ServerAssignmentRequest.In request, final PublicIdentity identity)
        throws DiameterErrorAnswerException {
        return outcome.createAnswer(request);
    }
}
