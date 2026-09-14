package com.sipgate.sparta.hss.diameter.s6a.clr;

import com.sipgate.sparta.hss.diameter.client.DiameterSessions;
import com.sipgate.sparta.hss.diameter.common.BaseOutboundListener;

public class CancelLocationOutboundListener extends BaseOutboundListener {

    public CancelLocationOutboundListener(final DiameterSessions diameterSessions) {
        super(diameterSessions);
    }

    public void receiveRequest(final CancelLocationEvent event) {
        onEvent(event);
    }
}
