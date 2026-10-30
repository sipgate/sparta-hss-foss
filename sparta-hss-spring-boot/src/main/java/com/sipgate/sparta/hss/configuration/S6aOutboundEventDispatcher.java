package com.sipgate.sparta.hss.configuration;

import com.sipgate.sparta.hss.diameter.s6a.clr.CancelLocationEvent;
import com.sipgate.sparta.hss.diameter.s6a.clr.CancelLocationOutboundListener;
import com.sipgate.sparta.hss.diameter.s6a.isd.InsertSubscriberDataEvent;
import com.sipgate.sparta.hss.diameter.s6a.isd.InsertSubscriberDataOutboundListener;
import com.sipgate.sparta.hss.service.subscription.SimDeactivated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

public class S6aOutboundEventDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(S6aOutboundEventDispatcher.class);

    private final CancelLocationOutboundListener cancelLocationOutboundListener;
    private final InsertSubscriberDataOutboundListener insertSubscriberDataOutboundListener;

    public S6aOutboundEventDispatcher(
        final CancelLocationOutboundListener cancelLocationOutboundListener,
        final InsertSubscriberDataOutboundListener insertSubscriberDataOutboundListener) {
        this.cancelLocationOutboundListener = cancelLocationOutboundListener;
        this.insertSubscriberDataOutboundListener = insertSubscriberDataOutboundListener;
    }

    // Dispatches asynchronously, so a slow or stalled send never blocks the thread that published the event.
    @Async("outboundThreadPoolExecutor")
    @EventListener
    public void onCancelLocation(final CancelLocationEvent event) {
        cancelLocationOutboundListener.receiveRequest(event);
    }

    @EventListener
    public void onSimDeactivated(final SimDeactivated event) {
        try {
            cancelLocationOutboundListener.receiveSimDeactivated(event);
        } catch (final Exception e) {
            LOGGER.error("could not handle SimDeactivated event", e);
        }
    }

    @Async("outboundThreadPoolExecutor")
    @EventListener
    public void onInsertSubscriberData(final InsertSubscriberDataEvent event) {
        insertSubscriberDataOutboundListener.receiveRequest(event);
    }
}
