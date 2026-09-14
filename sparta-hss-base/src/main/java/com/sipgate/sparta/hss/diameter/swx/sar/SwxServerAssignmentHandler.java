package com.sipgate.sparta.hss.diameter.swx.sar;

import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.SERVER_ASSIGNMENT_ADMINISTRATIVE_DEREGISTRATION;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.SERVER_ASSIGNMENT_AUTHENTICATION_FAILURE;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.SERVER_ASSIGNMENT_AUTHENTICATION_TIMEOUT;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.SERVER_ASSIGNMENT_DEREGISTRATION_TOO_MUCH_DATA;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.SERVER_ASSIGNMENT_NO_ASSIGNMENT;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.SERVER_ASSIGNMENT_RE_REGISTRATION;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.SERVER_ASSIGNMENT_REGISTRATION;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.SERVER_ASSIGNMENT_TIMEOUT_DEREGISTRATION;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.SERVER_ASSIGNMENT_TIMEOUT_DEREGISTRATION_STORE_SERVER_NAME;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.SERVER_ASSIGNMENT_UNREGISTERED_USER;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.SERVER_ASSIGNMENT_USER_DEREGISTRATION;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.SERVER_ASSIGNMENT_USER_DEREGISTRATION_STORE_SERVER_NAME;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.RES_DIAMETER_MISSING_AVP;

import com.sipgate.sparta.diameter._3gpp.swx.messages.ServerAssignmentAnswer;
import com.sipgate.sparta.diameter._3gpp.swx.messages.ServerAssignmentRequest;
import com.sipgate.sparta.diameter.base.core.DiameterMessageFactory;
import com.sipgate.sparta.hss.diameter.client.RegisterableDiameterHandler;
import com.sipgate.sparta.hss.diameter.swx.sar.assigntypehandler.DeregisterAaaHandler;
import com.sipgate.sparta.hss.diameter.swx.sar.assigntypehandler.NoopHandler;
import com.sipgate.sparta.hss.diameter.swx.sar.assigntypehandler.RegisterHandler;
import com.sipgate.sparta.hss.diameter.swx.sar.assigntypehandler.SwxServerAssignTypeHandler;
import com.sipgate.sparta.hss.event.EventPublisher;
import com.sipgate.sparta.hss.persistence.ImsiProfileDao;
import com.sipgate.sparta.hss.persistence.LocationVowifiDao;
import com.sipgate.sparta.hss.persistence.SimDao;
import jakarta.transaction.Transactional;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// SWx Server-Assignment-Request handler (3GPP TS 29.273 §8.1.2.1.3). The HSS is the SWx server:
/// a 3GPP AAA Server sends a SAR. REGISTRATION / RE_REGISTRATION are routed to [RegisterHandler]
/// (answers 2001 with the Non-3GPP-User-Data subscription profile); the de-registration types
/// (USER / ADMINISTRATIVE / AUTHENTICATION_FAILURE / AUTHENTICATION_TIMEOUT) are routed to
/// [DeregisterAaaHandler] (clears the AAA-Server assignment, answers 2001, no Non-3GPP-User-Data).
/// All other Server-Assignment-Types are answered `DIAMETER_UNABLE_TO_COMPLY`.
///
/// Mirrors the [Cx dispatch](com.sipgate.sparta.hss.diameter.cx.sar.ServerAssignmentHandler)
/// structure (strategy handlers keyed by Server-Assignment-Type).
@Transactional
public class SwxServerAssignmentHandler
    implements RegisterableDiameterHandler<ServerAssignmentRequest.In, ServerAssignmentAnswer.Out> {

    private static final int SERVER_ASSIGNMENT_TYPE_ABSENT = -1;

    private final Logger LOGGER = LoggerFactory.getLogger(SwxServerAssignmentHandler.class);

    private final Map<Integer, SwxServerAssignTypeHandler> handlers;
    private final SwxServerAssignTypeHandler noopHandler = new NoopHandler();
    private final MeterRegistry meterRegistry;

    public SwxServerAssignmentHandler(
        final SimDao simDao,
        final LocationVowifiDao locationVowifiDao,
        final ImsiProfileDao imsiProfileDao,
        final Non3gppUserDataFactory non3gppUserDataFactory,
        final EventPublisher eventPublisher,
        final MeterRegistry meterRegistry)
    {
        this.meterRegistry = meterRegistry;
        final var deregister = new DeregisterAaaHandler(simDao, locationVowifiDao, eventPublisher);
        final var register = new RegisterHandler(
            simDao, locationVowifiDao, imsiProfileDao, non3gppUserDataFactory, eventPublisher);
        this.handlers = new HashMap<>();
        this.handlers.put(SERVER_ASSIGNMENT_USER_DEREGISTRATION, deregister);
        this.handlers.put(SERVER_ASSIGNMENT_ADMINISTRATIVE_DEREGISTRATION, deregister);
        this.handlers.put(SERVER_ASSIGNMENT_AUTHENTICATION_FAILURE, deregister);
        this.handlers.put(SERVER_ASSIGNMENT_AUTHENTICATION_TIMEOUT, deregister);
        this.handlers.put(SERVER_ASSIGNMENT_REGISTRATION, register);
        this.handlers.put(SERVER_ASSIGNMENT_RE_REGISTRATION, register);
    }

    @Override
    public Class<ServerAssignmentRequest.In> requestType() {
        return ServerAssignmentRequest.In.class;
    }

    @Override
    public CompletableFuture<ServerAssignmentAnswer.Out> handle(final ServerAssignmentRequest.In request) {
        return CompletableFuture.completedFuture(doHandle(request));
    }

    private ServerAssignmentAnswer.Out doHandle(final ServerAssignmentRequest.In request) {
        if (request.getOriginHost() == null) {
            return SwxServerAssignTypeHandler.stampUserName(request,
                DiameterMessageFactory.createAnswer(request, RES_DIAMETER_MISSING_AVP));
        }
        if (request.getServerAssignmentType() == SERVER_ASSIGNMENT_TYPE_ABSENT) {
            return SwxServerAssignTypeHandler.stampUserName(request,
                DiameterMessageFactory.createAnswer(request, RES_DIAMETER_MISSING_AVP));
        }

        LOGGER.info("Handling SWX SAR / {} request for Identity: {}", request.getServerAssignmentType(), request.getUserName());
        meterRegistry.counter("diameter_server_assignment_request_type", "interface", "swx", "type", getAssignmentTypeName(request.getServerAssignmentType()))
                .increment();

        return handlers.getOrDefault(request.getServerAssignmentType(), noopHandler).handle(request);
    }

    private String getAssignmentTypeName(final int serverAssignmentType) {
        return switch (serverAssignmentType) {
            case SERVER_ASSIGNMENT_NO_ASSIGNMENT -> "no-assignment";
            case SERVER_ASSIGNMENT_REGISTRATION -> "register";
            case SERVER_ASSIGNMENT_RE_REGISTRATION -> "reregister";
            case SERVER_ASSIGNMENT_UNREGISTERED_USER -> "unregistered-user";
            case SERVER_ASSIGNMENT_TIMEOUT_DEREGISTRATION -> "timeout-deregistration";
            case SERVER_ASSIGNMENT_USER_DEREGISTRATION -> "user-deregistration";
            case SERVER_ASSIGNMENT_TIMEOUT_DEREGISTRATION_STORE_SERVER_NAME -> "timeout-deregistration-store-server-name";
            case SERVER_ASSIGNMENT_USER_DEREGISTRATION_STORE_SERVER_NAME -> "user-deregistration-store-server-name";
            case SERVER_ASSIGNMENT_ADMINISTRATIVE_DEREGISTRATION -> "admin-unregister";
            case SERVER_ASSIGNMENT_AUTHENTICATION_FAILURE -> "authentication-failure";
            case SERVER_ASSIGNMENT_AUTHENTICATION_TIMEOUT -> "authentication-timeout";
            case SERVER_ASSIGNMENT_DEREGISTRATION_TOO_MUCH_DATA -> "deregistration-too-much-data";
            default -> "unknown";
        };
    }
}
