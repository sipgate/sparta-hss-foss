package com.sipgate.sparta.hss.diameter.client;

import com.sipgate.sparta.diameter.base.core.IncomingRequest;
import com.sipgate.sparta.diameter.base.core.OutgoingAnswer;
import com.sipgate.sparta.diameter.base.session.DiameterRequestHandler;

/// A Diameter request handler that tells the connection layer which request type it serves.
/// [DiameterConnectionHandler] registers each handler for exactly [#requestType()] on every peer
/// session, so the set of served interfaces is simply the set of handlers passed to it.
public interface RegisterableDiameterHandler<R extends IncomingRequest<A>, A extends OutgoingAnswer>
        extends DiameterRequestHandler<R, A> {

    Class<R> requestType();
}
