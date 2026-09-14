package com.sipgate.sparta.hss.diameter.swx.sar.assigntypehandler;

import static com.sipgate.sparta.diameter.base.core.DiameterConstants.RES_DIAMETER_UNABLE_TO_COMPLY;

import com.sipgate.sparta.diameter._3gpp.swx.messages.ServerAssignmentAnswer;
import com.sipgate.sparta.diameter._3gpp.swx.messages.ServerAssignmentRequest;
import com.sipgate.sparta.diameter.base.core.DiameterMessageFactory;

/// No-op SWx SAR handler for everything slice 1 does not serve (registration, re-registration,
/// Cx-only de-registration variants, AAA-User-Data-Request, PGW-Update, unknown types). Slice 1
/// answers these with DIAMETER_UNABLE_TO_COMPLY and never returns Non-3GPP-User-Data.
public final class NoopHandler implements SwxServerAssignTypeHandler {

    @Override
    public ServerAssignmentAnswer.Out handle(final ServerAssignmentRequest.In request) {
        return SwxServerAssignTypeHandler.stampUserName(request,
            DiameterMessageFactory.createAnswer(request, RES_DIAMETER_UNABLE_TO_COMPLY));
    }
}
