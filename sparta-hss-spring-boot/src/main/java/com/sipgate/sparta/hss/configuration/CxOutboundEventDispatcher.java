package com.sipgate.sparta.hss.configuration;

import com.sipgate.sparta.hss.diameter.cx.rtr.RegistrationTerminationEvent;
import com.sipgate.sparta.hss.diameter.cx.rtr.RegistrationTerminationOutboundListener;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

/// Dispatches the outbound Cx request events (RTR) to their listener on the
/// `outboundThreadPoolExecutor`, so a slow or stalled send never blocks the thread that
/// published the event.
public class CxOutboundEventDispatcher {

    private final RegistrationTerminationOutboundListener registrationTerminationOutboundListener;

    public CxOutboundEventDispatcher(final RegistrationTerminationOutboundListener registrationTerminationOutboundListener) {
        this.registrationTerminationOutboundListener = registrationTerminationOutboundListener;
    }

    @Async("outboundThreadPoolExecutor")
    @EventListener
    public void onRegistrationTermination(final RegistrationTerminationEvent event) {
        registrationTerminationOutboundListener.receiveRequest(event);
    }
}
