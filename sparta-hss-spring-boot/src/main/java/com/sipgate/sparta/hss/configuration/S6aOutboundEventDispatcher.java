package com.sipgate.sparta.hss.configuration;

import com.sipgate.sparta.hss.diameter.s6a.clr.CancelLocationEvent;
import com.sipgate.sparta.hss.diameter.s6a.clr.CancelLocationOutboundListener;
import com.sipgate.sparta.hss.diameter.s6a.isd.InsertSubscriberDataEvent;
import com.sipgate.sparta.hss.diameter.s6a.isd.InsertSubscriberDataOutboundListener;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

/// Dispatches the outbound S6a request events (CLR, ISD) to their listeners on the
/// `outboundThreadPoolExecutor`, so a slow or stalled send never blocks the thread that
/// published the event.
public class S6aOutboundEventDispatcher {

    private final CancelLocationOutboundListener cancelLocationOutboundListener;
    private final InsertSubscriberDataOutboundListener insertSubscriberDataOutboundListener;

    public S6aOutboundEventDispatcher(
        final CancelLocationOutboundListener cancelLocationOutboundListener,
        final InsertSubscriberDataOutboundListener insertSubscriberDataOutboundListener) {
        this.cancelLocationOutboundListener = cancelLocationOutboundListener;
        this.insertSubscriberDataOutboundListener = insertSubscriberDataOutboundListener;
    }

    @Async("outboundThreadPoolExecutor")
    @EventListener
    public void onCancelLocation(final CancelLocationEvent event) {
        cancelLocationOutboundListener.receiveRequest(event);
    }

    @Async("outboundThreadPoolExecutor")
    @EventListener
    public void onInsertSubscriberData(final InsertSubscriberDataEvent event) {
        insertSubscriberDataOutboundListener.receiveRequest(event);
    }
}
