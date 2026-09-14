package com.sipgate.sparta.hss.diameter.common;

import com.sipgate.sparta.diameter.base.core.Answer;
import com.sipgate.sparta.diameter.base.core.OutgoingRequest;
import java.util.Objects;

/// Base for HSS-initiated outbound Diameter requests (RTR, CLR, ISD). A request handler that needs
/// to trigger an outbound request publishes a [BaseEvent] subclass via
/// [com.sipgate.sparta.hss.event.EventPublisher] instead of sending it directly; a matching
/// [BaseOutboundListener] sends it.
///
/// This keeps the inbound handlers free of the send mechanics (session lookup, Session-Id
/// generation, the answer future): the handler only describes *what* to send, the listener owns
/// *how* it is sent over the `sparta-diameter` client.
public abstract class BaseEvent {

    private final OutgoingRequest<? extends Answer> request;
    private final String context;

    /// @param request the fully built outgoing request, except the Session-Id which the listener
    ///                stamps from the session layer just before sending
    /// @param context a short human-readable description (e.g. `"user=..., to=..."`) for logging
    protected BaseEvent(final OutgoingRequest<? extends Answer> request, final String context) {
        this.request = Objects.requireNonNull(request);
        this.context = context;
    }

    public OutgoingRequest<? extends Answer> getRequest() {
        return request;
    }

    public String getContext() {
        return context;
    }
}
