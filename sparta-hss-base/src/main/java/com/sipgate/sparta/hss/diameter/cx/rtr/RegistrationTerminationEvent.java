package com.sipgate.sparta.hss.diameter.cx.rtr;

import com.sipgate.sparta.diameter._3gpp.cxdx.messages.RegistrationTerminationRequest;
import com.sipgate.sparta.hss.diameter.common.BaseEvent;

/// Published when an S-CSCF reassignment requires deregistering the subscriber at its previous
/// S-CSCF (Cx Registration-Termination-Request, TS 29.228 §8.1.1).
public class RegistrationTerminationEvent extends BaseEvent {

    public RegistrationTerminationEvent(final RegistrationTerminationRequest.Out request, final String context) {
        super(request, context);
    }
}
