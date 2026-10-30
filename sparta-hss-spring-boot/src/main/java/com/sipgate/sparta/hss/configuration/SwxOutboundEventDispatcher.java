package com.sipgate.sparta.hss.configuration;

import com.sipgate.sparta.hss.diameter.swx.rtr.SwxRegistrationTerminationOutboundListener;
import com.sipgate.sparta.hss.service.subscription.SimDeactivated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;

public class SwxOutboundEventDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(SwxOutboundEventDispatcher.class);

    private final SwxRegistrationTerminationOutboundListener swxRegistrationTerminationOutboundListener;

    public SwxOutboundEventDispatcher(
        final SwxRegistrationTerminationOutboundListener swxRegistrationTerminationOutboundListener) {
        this.swxRegistrationTerminationOutboundListener = swxRegistrationTerminationOutboundListener;
    }

    @EventListener
    public void onSimDeactivated(final SimDeactivated event) {
        try {
            swxRegistrationTerminationOutboundListener.receiveSimDeactivated(event);
        } catch (final Exception e) {
            LOGGER.error("could not handle SimDeactivated event", e);
        }
    }
}
