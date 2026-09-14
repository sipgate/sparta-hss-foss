package com.sipgate.sparta.hss.diameter.s6a.isd;

import com.sipgate.sparta.diameter._3gpp.s6a.messages.InsertSubscriberDataRequest;
import com.sipgate.sparta.hss.diameter.common.BaseEvent;

/// Published when updated subscription data must be pushed to the MME currently serving the
/// subscriber (S6a Insert-Subscriber-Data-Request, TS 29.272 §7.2.9).
public class InsertSubscriberDataEvent extends BaseEvent {

    public InsertSubscriberDataEvent(final InsertSubscriberDataRequest.Out request, final String context) {
        super(request, context);
    }
}
