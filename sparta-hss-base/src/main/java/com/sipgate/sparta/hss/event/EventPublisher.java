package com.sipgate.sparta.hss.event;

/// Extension point of the HSS core: handlers and services publish their domain events (e.g.
/// [com.sipgate.sparta.hss.diameter.s6a.ulr.LocationUpdated] or the
/// [com.sipgate.sparta.hss.diameter.common.BaseEvent] outbound-request events) through this
/// interface instead of talking to listeners directly.
///
/// The hosting application provides the implementation and with it the dispatch semantics:
/// which listeners receive an event, and on which thread. Events carrying outbound Diameter
/// requests must reach their [com.sipgate.sparta.hss.diameter.common.BaseOutboundListener]
/// off the inbound request thread.
public interface EventPublisher {

    void publish(Object event);
}
