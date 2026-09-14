package com.sipgate.sparta.hss.diameter.common;

import com.sipgate.sparta.hss.diameter.client.DiameterSessions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Objects;

/// Sends the outbound request carried by a [BaseEvent] over the Diameter client and logs the
/// outcome.
///
/// Answer handling is fire-and-forget: the DRA routes the request by Destination-Host/Realm, and
/// the HSS does not act on the answer beyond logging.
public abstract class BaseOutboundListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseOutboundListener.class);

    private final DiameterSessions diameterSessions;

    protected BaseOutboundListener(final DiameterSessions diameterSessions) {
        this.diameterSessions = diameterSessions;
    }

    protected void onEvent(final BaseEvent event) {
        final var request = event.getRequest();

        // The Session-Id (RFC 6733 §8.8) is a session-scoped identifier owned by the session layer:
        // the producer builds the command content, the listener stamps the Session-Id before sending.
        Objects.requireNonNull(request.getSessionId());

        final var command = commandName(request.getClass());
        diameterSessions.send(request).whenComplete((answer, error) -> {
            if (error == null) {
                LOGGER.info("sent outbound {} ({})", command, event.getContext());
            } else {
                LOGGER.warn("outbound {} ({}) failed: {}", command, event.getContext(), error.getMessage());
            }
        });
    }

    /// The request is a nested `.Out` class, so the enclosing class carries the command name
    /// (e.g. `RegistrationTerminationRequest` for `RegistrationTerminationRequest.Out`).
    private static String commandName(final Class<?> requestClass) {
        final var enclosing = requestClass.getEnclosingClass();
        return (enclosing != null ? enclosing : requestClass).getSimpleName();
    }
}
