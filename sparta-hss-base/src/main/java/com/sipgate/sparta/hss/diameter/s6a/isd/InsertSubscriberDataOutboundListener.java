package com.sipgate.sparta.hss.diameter.s6a.isd;

import com.sipgate.sparta.hss.diameter.client.DiameterSessions;
import com.sipgate.sparta.hss.diameter.common.BaseOutboundListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InsertSubscriberDataOutboundListener extends BaseOutboundListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(InsertSubscriberDataOutboundListener.class);

    public InsertSubscriberDataOutboundListener(final DiameterSessions diameterSessions) {
        super(diameterSessions);
    }

    /// Runs synchronously in the publisher's transaction; a failing send is caught here so it can
    /// neither mark the publisher's transaction rollback-only nor fail the triggering request.
    public void receiveRequest(final InsertSubscriberDataEvent event) {
        try {
            onEvent(event);
        } catch (final Exception e) {
            LOGGER.error("failed to send outbound insert subscriber data ({})", event.getContext(), e);
        }
    }
}
