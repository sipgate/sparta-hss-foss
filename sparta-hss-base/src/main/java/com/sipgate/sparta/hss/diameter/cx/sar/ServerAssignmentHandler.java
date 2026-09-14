package com.sipgate.sparta.hss.diameter.cx.sar;

import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.VENDOR_ID_3GPP;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.*;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.AVP_USER_NAME;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.RES_DIAMETER_SUCCESS;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.RES_DIAMETER_UNABLE_TO_COMPLY;

import com.sipgate.sparta.diameter._3gpp.cxdx.messages.ServerAssignmentAnswer;
import com.sipgate.sparta.diameter._3gpp.cxdx.messages.ServerAssignmentRequest;
import com.sipgate.sparta.diameter.base.core.avp.AVP;
import com.sipgate.sparta.diameter.base.core.avp.AVPKey;
import com.sipgate.sparta.diameter.base.session.DiameterErrorAnswerException;
import com.sipgate.sparta.hss.diameter.client.RegisterableDiameterHandler;
import com.sipgate.sparta.hss.diameter.common.AnswerFactory;
import com.sipgate.sparta.hss.diameter.cx.sar.assigntypehandler.*;
import com.sipgate.sparta.hss.diameter.cx.sar.identity.PublicIdentity;
import com.sipgate.sparta.hss.diameter.cx.sar.service.ImsService;
import com.sipgate.sparta.hss.diameter.cx.sar.service.SimService;
import com.sipgate.sparta.hss.event.EventPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.transaction.Transactional;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerAssignmentHandler implements RegisterableDiameterHandler<ServerAssignmentRequest.In, ServerAssignmentAnswer.Out> {

    private static final AVPKey KEY_USER_NAME = new AVPKey(AVP_USER_NAME, 0);
    private static final AVPKey KEY_PUBLIC_IDENTITY = new AVPKey(AVP_PUBLIC_IDENTITY, VENDOR_ID_3GPP);

    private final Logger LOGGER = LoggerFactory.getLogger(ServerAssignmentHandler.class);
    private final SimService simService;
    private final ImsService imsService;
    private final MeterRegistry meterRegistry;

    private final ServerAssignTypeHandler unknownAssignTypeHandler = new NoopHandler(new SaaOutcome.Error(RES_DIAMETER_UNABLE_TO_COMPLY));
    private final EventPublisher eventPublisher;
    private Map<Integer, ServerAssignTypeHandler> handlers;

    public ServerAssignmentHandler(final SimService simService, final ImsService imsService, final MeterRegistry meterRegistry, final EventPublisher eventPublisher) {
        this.simService = simService;
        this.imsService = imsService;
        this.meterRegistry = meterRegistry;
        this.eventPublisher = eventPublisher;
    }

    private static String valueOfNullableList(final List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }

        return values.getFirst();
    }


    @Override
    public Class<ServerAssignmentRequest.In> requestType() {
        return ServerAssignmentRequest.In.class;
    }

    @Override
    @Transactional
    public CompletableFuture<ServerAssignmentAnswer.Out> handle(final ServerAssignmentRequest.In request) {
        try {
            return doHandle(request);
        } catch (final Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }


    public CompletableFuture<ServerAssignmentAnswer.Out> doHandle(final ServerAssignmentRequest.In request) throws DiameterErrorAnswerException {
        final var privateIdentityValue = request.getUserName();
        final var publicIdentities = request.getPublicIdentities();
        final var publicIdentityValue = valueOfNullableList(publicIdentities);

        // RFC 6733 §7.5: Failed-AVP refers to the first AVP processing error encountered
        if (privateIdentityValue == null) {
            throw AnswerFactory.missingAvp(request, AVP.create(KEY_USER_NAME, ""));
        }
        if (publicIdentityValue == null) {
            throw AnswerFactory.missingAvp(request, AVP.create(KEY_PUBLIC_IDENTITY, ""));
        }

        LOGGER.info("Handling CX SAR / {} request for Identity: {}", request.getServerAssignmentType(), publicIdentityValue);
        meterRegistry.counter("diameter_server_assignment_request_type",  "interface", "cx", "type", getAssignmentTypeName(request.getServerAssignmentType()))
                .increment();

        final var publicIdentity = PublicIdentity.parse(publicIdentityValue, simService);
        if(publicIdentity == null) {
            throw AnswerFactory.invalidAvpValue(request, AVP.create(KEY_PUBLIC_IDENTITY, publicIdentityValue));
        }
        final var handler = getHandlersLazy().getOrDefault(request.getServerAssignmentType(), unknownAssignTypeHandler);

        return CompletableFuture.completedFuture(handler.handle(request, publicIdentity));
    }

    private Map<Integer, ServerAssignTypeHandler> getHandlersLazy() {
        if (handlers != null) {
            return handlers;
        }

        handlers = new HashMap<>();
        handlers.put(SERVER_ASSIGNMENT_UNREGISTERED_USER, new NoopHandler(new SaaOutcome.ExperimentalError(EXP_RES_DIAMETER_ERROR_FEATURE_UNSUPPORTED)));

        final ServerAssignTypeHandler successNoop = new ValidatingHandler(new NoopHandler(new SaaOutcome.Success(RES_DIAMETER_SUCCESS)), simService);
        handlers.put(SERVER_ASSIGNMENT_AUTHENTICATION_FAILURE, successNoop);
        handlers.put(SERVER_ASSIGNMENT_AUTHENTICATION_TIMEOUT, successNoop);

        final ServerAssignTypeHandler deregisterSuccess = new ValidatingHandler(new DeregistrationHandler(imsService, new SaaOutcome.Success(RES_DIAMETER_SUCCESS), eventPublisher), simService);
        handlers.put(SERVER_ASSIGNMENT_TIMEOUT_DEREGISTRATION, deregisterSuccess);
        handlers.put(SERVER_ASSIGNMENT_USER_DEREGISTRATION, deregisterSuccess);
        handlers.put(SERVER_ASSIGNMENT_ADMINISTRATIVE_DEREGISTRATION, deregisterSuccess);
        handlers.put(SERVER_ASSIGNMENT_DEREGISTRATION_TOO_MUCH_DATA, deregisterSuccess);

        final ServerAssignTypeHandler deregisterNotStored = new ValidatingHandler(new DeregistrationHandler(imsService, new SaaOutcome.ExperimentalSuccess(EXP_RES_DIAMETER_SUCCESS_SERVER_NAME_NOT_STORED), eventPublisher), simService);
        handlers.put(SERVER_ASSIGNMENT_TIMEOUT_DEREGISTRATION_STORE_SERVER_NAME, deregisterNotStored);
        handlers.put(SERVER_ASSIGNMENT_USER_DEREGISTRATION_STORE_SERVER_NAME, deregisterNotStored);

        final ServerAssignTypeHandler register = new ValidatingHandler(new RegisterHandler(imsService, eventPublisher), simService);
        handlers.put(SERVER_ASSIGNMENT_REGISTRATION, register);
        handlers.put(SERVER_ASSIGNMENT_RE_REGISTRATION, register);
        return handlers;
    }

    private String getAssignmentTypeName(final int serverAssignmentType) {
        return switch (serverAssignmentType) {
            case SERVER_ASSIGNMENT_NO_ASSIGNMENT -> "no-assignment";
            case SERVER_ASSIGNMENT_REGISTRATION -> "register";
            case SERVER_ASSIGNMENT_RE_REGISTRATION -> "reregister";
            case SERVER_ASSIGNMENT_UNREGISTERED_USER -> "unregistered-user";
            case SERVER_ASSIGNMENT_TIMEOUT_DEREGISTRATION -> "timeout-unregister";
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
