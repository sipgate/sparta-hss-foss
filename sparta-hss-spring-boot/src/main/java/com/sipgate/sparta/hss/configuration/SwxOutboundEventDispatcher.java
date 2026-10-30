package com.sipgate.sparta.hss.configuration;

import com.sipgate.sparta.hss.diameter.swx.rtr.SwxRegistrationTerminationOutboundListener;
import com.sipgate.sparta.hss.service.subscription.SimDeactivated;
import org.springframework.context.event.EventListener;

/// Dispatches the events that trigger outbound SWx requests (RTR) to their listener
/// synchronously, on the publishing thread and inside the publisher's transaction.
///
/// Synchronous dispatch is deliberate: the listener consults the `AccessPolicy` in the publisher's
/// transaction, so a `SimDeactivated` published by `SubscriptionStateService.deactivate` is observed
/// with its uncommitted `isActive=false` (read-your-own-writes — no race between the flag write and
/// the teardown decision), and kafka message order stays the execution order. The listener guards
/// itself with try/catch, so a failed teardown is logged and dropped without failing the publisher
/// or marking its transaction rollback-only.
public class SwxOutboundEventDispatcher {

    private final SwxRegistrationTerminationOutboundListener swxRegistrationTerminationOutboundListener;

    public SwxOutboundEventDispatcher(
        final SwxRegistrationTerminationOutboundListener swxRegistrationTerminationOutboundListener) {
        this.swxRegistrationTerminationOutboundListener = swxRegistrationTerminationOutboundListener;
    }

    @EventListener
    public void onSimDeactivated(final SimDeactivated event) {
        swxRegistrationTerminationOutboundListener.receiveSimDeactivated(event);
    }
}
