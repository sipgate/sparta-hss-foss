package com.sipgate.sparta.hss.configuration;

import com.sipgate.sparta.hss.diameter.cx.rtr.RegistrationTerminationEvent;
import com.sipgate.sparta.hss.diameter.cx.rtr.RegistrationTerminationOutboundListener;
import com.sipgate.sparta.hss.service.subscription.SimDeactivated;
import org.springframework.context.event.EventListener;

/// Dispatches the outbound Cx request events (RTR) to their listener synchronously, on the
/// publishing thread and inside the publisher's transaction.
///
/// Synchronous dispatch is deliberate: the listener consults the `AccessPolicy` in the publisher's
/// transaction, so a `SimDeactivated` published by `SubscriptionStateService.deactivate` is observed
/// with its uncommitted `isActive=false` (read-your-own-writes — no race between the flag write and
/// the teardown decision), and kafka message order stays the execution order. The listener guards
/// itself with try/catch, so a failed teardown is logged and dropped without failing the publisher
/// or marking its transaction rollback-only.
public class CxOutboundEventDispatcher {

    private final RegistrationTerminationOutboundListener registrationTerminationOutboundListener;

    public CxOutboundEventDispatcher(final RegistrationTerminationOutboundListener registrationTerminationOutboundListener) {
        this.registrationTerminationOutboundListener = registrationTerminationOutboundListener;
    }

    @EventListener
    public void onRegistrationTermination(final RegistrationTerminationEvent event) {
        registrationTerminationOutboundListener.receiveRequest(event);
    }

    @EventListener
    public void onSimDeactivated(final SimDeactivated event) {
        registrationTerminationOutboundListener.receiveSimDeactivated(event);
    }
}
