package com.sipgate.sparta.hss.configuration;

import com.sipgate.sparta.hss.diameter.s6a.clr.CancelLocationEvent;
import com.sipgate.sparta.hss.diameter.s6a.clr.CancelLocationOutboundListener;
import com.sipgate.sparta.hss.diameter.s6a.isd.InsertSubscriberDataEvent;
import com.sipgate.sparta.hss.diameter.s6a.isd.InsertSubscriberDataOutboundListener;
import com.sipgate.sparta.hss.service.subscription.SimDeactivated;
import org.springframework.context.event.EventListener;

/// Dispatches the outbound S6a request events (CLR, ISD) to their listeners synchronously, on the
/// publishing thread and inside the publisher's transaction.
///
/// Synchronous dispatch is deliberate: the listeners consult the `AccessPolicy` in the publisher's
/// transaction, so a `SimDeactivated` published by `SubscriptionStateService.deactivate` is observed
/// with its uncommitted `isActive=false` (read-your-own-writes — no race between the flag write and
/// the teardown decision), and kafka message order stays the execution order. The listeners guard
/// themselves with try/catch, so a failed teardown is logged and dropped without failing the publisher
/// or marking its transaction rollback-only.
public class S6aOutboundEventDispatcher {

    private final CancelLocationOutboundListener cancelLocationOutboundListener;
    private final InsertSubscriberDataOutboundListener insertSubscriberDataOutboundListener;

    public S6aOutboundEventDispatcher(
        final CancelLocationOutboundListener cancelLocationOutboundListener,
        final InsertSubscriberDataOutboundListener insertSubscriberDataOutboundListener) {
        this.cancelLocationOutboundListener = cancelLocationOutboundListener;
        this.insertSubscriberDataOutboundListener = insertSubscriberDataOutboundListener;
    }

    @EventListener
    public void onCancelLocation(final CancelLocationEvent event) {
        cancelLocationOutboundListener.receiveRequest(event);
    }

    @EventListener
    public void onSimDeactivated(final SimDeactivated event) {
        cancelLocationOutboundListener.receiveSimDeactivated(event);
    }

    @EventListener
    public void onInsertSubscriberData(final InsertSubscriberDataEvent event) {
        insertSubscriberDataOutboundListener.receiveRequest(event);
    }
}
