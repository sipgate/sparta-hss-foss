package com.sipgate.sparta.hss.diameter.common;

import com.sipgate.sparta.diameter.base.core.Answer;
import com.sipgate.sparta.diameter.base.core.OutgoingRequest;
import com.sipgate.sparta.hss.diameter.client.DiameterSessions;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Sends outbound requests over the Diameter client and logs the outcome: the request carried by
/// a [BaseEvent], or requests a subclass builds itself from a domain event (e.g. on SIM
/// deactivation).
///
/// Answer handling is fire-and-forget: the DRA routes the request by Destination-Host/Realm, and
/// the HSS does not act on the answer beyond logging.
public abstract class BaseOutboundListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseOutboundListener.class);

    private final DiameterSessions diameterSessions;

    protected BaseOutboundListener(final DiameterSessions diameterSessions) {
        this.diameterSessions = diameterSessions;
    }

    protected DiameterSessions getDiameterSessions() {
        return diameterSessions;
    }

    protected void onEvent(final BaseEvent event) {
        send(event.getRequest(), event.getContext());
    }

    /// Sends a request built by the producer ([#onEvent]) or by the listener itself from a domain
    /// event; `context` is a short description for logging (e.g. `"imsi=..., mme=..."`).
    protected void send(final OutgoingRequest<? extends Answer> request, final String context) {
        // The Session-Id (RFC 6733 §8.8) is a session-scoped identifier owned by the session layer:
        // the producer builds the command content, the listener stamps the Session-Id before sending.
        Objects.requireNonNull(request.getSessionId());

        final var command = commandName(request.getClass());
        diameterSessions.send(request).whenComplete((answer, error) -> {
            if (error != null) {
                LOGGER.warn("outbound {} ({}) failed: {}", command, context, error.getMessage());
                return;
            }
            LOGGER.info("sent outbound {} ({})", command, context);
        });
    }

    /// The request is a nested `.Out` class, so the enclosing class carries the command name
    /// (e.g. `RegistrationTerminationRequest` for `RegistrationTerminationRequest.Out`).
    private static String commandName(final Class<?> requestClass) {
        final var enclosing = requestClass.getEnclosingClass();
        return (enclosing != null ? enclosing : requestClass).getSimpleName();
    }
}
