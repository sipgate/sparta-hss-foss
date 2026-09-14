package com.sipgate.sparta.hss.diameter.cx.rtr;

import com.sipgate.sparta.hss.diameter.client.DiameterSessions;
import com.sipgate.sparta.hss.diameter.common.BaseOutboundListener;

public class RegistrationTerminationOutboundListener extends BaseOutboundListener {

    public RegistrationTerminationOutboundListener(final DiameterSessions diameterSessions) {
        super(diameterSessions);
    }

    public void receiveRequest(final RegistrationTerminationEvent event) {
        onEvent(event);
    }
}
