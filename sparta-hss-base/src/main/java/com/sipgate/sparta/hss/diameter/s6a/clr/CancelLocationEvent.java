package com.sipgate.sparta.hss.diameter.s6a.clr;

import com.sipgate.sparta.diameter._3gpp.s6a.messages.CancelLocationRequest;
import com.sipgate.sparta.hss.diameter.common.BaseEvent;

/// Published when a UE attaches to a new MME and the previous MME must be told to drop the
/// subscriber (S6a Cancel-Location-Request, TS 29.272 §7.2.7).
public class CancelLocationEvent extends BaseEvent {

    public CancelLocationEvent(final CancelLocationRequest.Out request, final String context) {
        super(request, context);
    }
}
