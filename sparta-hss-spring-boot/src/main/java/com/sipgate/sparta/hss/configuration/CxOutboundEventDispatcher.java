package com.sipgate.sparta.hss.configuration;

import com.sipgate.sparta.hss.diameter.cx.rtr.RegistrationTerminationEvent;
import com.sipgate.sparta.hss.diameter.cx.rtr.RegistrationTerminationOutboundListener;
import com.sipgate.sparta.hss.service.subscription.SimDeactivated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

public class CxOutboundEventDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(CxOutboundEventDispatcher.class);

    private final RegistrationTerminationOutboundListener registrationTerminationOutboundListener;

    public CxOutboundEventDispatcher(final RegistrationTerminationOutboundListener registrationTerminationOutboundListener) {
        this.registrationTerminationOutboundListener = registrationTerminationOutboundListener;
    }

    // Dispatches asynchronously, so a slow or stalled send never blocks the thread that published the event.
    @Async("outboundThreadPoolExecutor")
    @EventListener
    public void onRegistrationTermination(final RegistrationTerminationEvent event) {
        registrationTerminationOutboundListener.receiveRequest(event);
    }

    /// Dispatches after the deactivating transaction committed, so the teardown observes the
    /// committed `sim.isActive` state and cannot poison the deactivation itself: a listener
    /// failure neither rolls back the deactivation nor blocks the caller that caused it.
    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void onSimDeactivated(final SimDeactivated event) {
        try {
            registrationTerminationOutboundListener.receiveSimDeactivated(event);
        } catch (final Exception e) {
            LOGGER.error("could not handle SimDeactivated event", e);
        }
    }
}
