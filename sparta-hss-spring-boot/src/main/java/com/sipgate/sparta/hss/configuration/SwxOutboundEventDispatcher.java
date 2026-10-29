package com.sipgate.sparta.hss.configuration;

import com.sipgate.sparta.hss.diameter.swx.rtr.SwxRegistrationTerminationOutboundListener;
import com.sipgate.sparta.hss.service.subscription.SimDeactivated;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

/// Dispatches the events that trigger outbound SWx requests (RTR) to their listener on the
/// `outboundThreadPoolExecutor`, so a slow or stalled send never blocks the thread that
/// published the event.
public class SwxOutboundEventDispatcher {

    private final SwxRegistrationTerminationOutboundListener swxRegistrationTerminationOutboundListener;

    public SwxOutboundEventDispatcher(
        final SwxRegistrationTerminationOutboundListener swxRegistrationTerminationOutboundListener) {
        this.swxRegistrationTerminationOutboundListener = swxRegistrationTerminationOutboundListener;
    }

    @Async("outboundThreadPoolExecutor")
    @EventListener
    public void onSimDeactivated(final SimDeactivated event) {
        swxRegistrationTerminationOutboundListener.receiveSimDeactivated(event);
    }
}
