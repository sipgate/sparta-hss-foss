package com.sipgate.sparta.hss.diameter.s6a.isd;

import com.sipgate.sparta.hss.diameter.client.DiameterSessions;
import com.sipgate.sparta.hss.diameter.common.BaseOutboundListener;

public class InsertSubscriberDataOutboundListener extends BaseOutboundListener {

    public InsertSubscriberDataOutboundListener(final DiameterSessions diameterSessions) {
        super(diameterSessions);
    }

    public void receiveRequest(final InsertSubscriberDataEvent event) {
        onEvent(event);
    }
}
