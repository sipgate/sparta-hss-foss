package com.sipgate.sparta.hss.diameter.cx.sar.assigntypehandler;

import com.sipgate.sparta.diameter._3gpp.cxdx.messages.ServerAssignmentAnswer;
import com.sipgate.sparta.diameter._3gpp.cxdx.messages.ServerAssignmentRequest;
import com.sipgate.sparta.diameter.base.session.DiameterErrorAnswerException;
import com.sipgate.sparta.hss.diameter.cx.sar.identity.PublicIdentity;

public abstract class ServerAssignTypeHandler {

    private static final int IMSI_LENGTH = 15;

    static String getImsiFromRequest(final ServerAssignmentRequest.In request) {
        final var userName = request.getUserName();
        if (userName == null || userName.length() < IMSI_LENGTH) {
            return "";
        }
        return userName.substring(0, IMSI_LENGTH);
    }

    public abstract ServerAssignmentAnswer.Out handle(ServerAssignmentRequest.In request, PublicIdentity identity)
        throws DiameterErrorAnswerException;
}
