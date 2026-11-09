package com.sipgate.sparta.hss.configuration;

import com.sipgate.sparta.hss.diameter.swx.rtr.SwxRegistrationTerminationOutboundListener;
import com.sipgate.sparta.hss.service.subscription.SimDeactivated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.springframework.transaction.event.TransactionPhase.*;

public class SwxOutboundEventDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(SwxOutboundEventDispatcher.class);

    private final SwxRegistrationTerminationOutboundListener swxRegistrationTerminationOutboundListener;

    public SwxOutboundEventDispatcher(
        final SwxRegistrationTerminationOutboundListener swxRegistrationTerminationOutboundListener) {
        this.swxRegistrationTerminationOutboundListener = swxRegistrationTerminationOutboundListener;
    }

    /// Dispatches after the deactivating transaction committed, so the teardown observes the
    /// committed `sim.isActive` state and cannot poison the deactivation itself: a listener
    /// failure neither rolls back the deactivation nor blocks the caller that caused it.
    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void onSimDeactivated(final SimDeactivated event) {
        try {
            swxRegistrationTerminationOutboundListener.receiveSimDeactivated(event);
        } catch (final Exception e) {
            LOGGER.error("could not handle SimDeactivated event", e);
        }
    }
}
