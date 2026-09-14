package com.sipgate.sparta.hss.diameter.client;

import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.VENDOR_ID_3GPP;

import com.sipgate.sparta.diameter.base.core.DiameterConstants;
import com.sipgate.sparta.diameter.base.core.IncomingRequest;
import com.sipgate.sparta.diameter.base.core.OutgoingAnswer;
import com.sipgate.sparta.diameter.base.core.avp.AVPKey;
import com.sipgate.sparta.diameter.base.core.avp.mixins.HasExperimentalResultAVP;
import com.sipgate.sparta.diameter.base.core.avp.mixins.HasOriginHostAVP;
import com.sipgate.sparta.diameter.base.core.avp.mixins.HasResultCodeAVP;
import com.sipgate.sparta.diameter.base.core.avp.mixins.HasUserNameAVP;
import com.sipgate.sparta.diameter.base.session.*;
import com.sipgate.sparta.diameter.base.transport.DiameterNode;
import com.sipgate.sparta.hss.diameter.common.auth.MncMccFormatter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class DiameterConnectionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(DiameterConnectionHandler.class);
    private static final long[] HIST_BARS_BUCKET_MILLIS = new long[]{25, 50, 100, 200, 400, 800, 1_600, 3_200, 6_400, 12_800,};

    private final MeterRegistry meterRegistry;
    private final DiameterNodeConfig config;
    private final DiameterSessions diameterSessions;
    private final Executor inboundExecutor;
    private final List<DiameterNode> nodes = new ArrayList<>();
    private final List<DiameterPeer> peers;

    private final List<RegisterableDiameterHandler<?, ?>> handlers;

    /// @param handlers the request handlers to serve; which interfaces the HSS answers is
    ///                 decided solely by this list. Each request type may have one handler.
    public DiameterConnectionHandler(
        final DiameterNodeConfig config,
        final List<DiameterPeer> peers,
        final MeterRegistry meterRegistry,
        final DiameterSessions diameterSessions,
        final Executor inboundExecutor,
        final List<RegisterableDiameterHandler<?, ?>> handlers) {
        this.config = config;
        this.peers = peers;
        this.meterRegistry = meterRegistry;
        this.diameterSessions = diameterSessions;
        this.inboundExecutor = inboundExecutor;
        this.handlers = List.copyOf(handlers);
        failOnDuplicateRequestTypes(this.handlers);
    }

    private static void failOnDuplicateRequestTypes(final List<RegisterableDiameterHandler<?, ?>> handlers) {
        final Map<Class<?>, RegisterableDiameterHandler<?, ?>> byRequestType = new HashMap<>();
        for (final var handler : handlers) {
            final var previous = byRequestType.putIfAbsent(handler.requestType(), handler);
            if (previous != null) {
                throw new IllegalArgumentException(
                    "both %s and %s want to handle %s".formatted(
                        previous.getClass().getName(), handler.getClass().getName(), handler.requestType().getName()));
            }
        }
    }

    public void connect() {
        LOGGER.info("Connecting to {} peers", peers.size());
        for (final var peer : peers) {
            connect(config, peer);
        }
    }

    static List<DiameterNodeConfig.VendorSpecificApp> vendorSpecificApps(final List<Long> authApplicationIds) {
        return authApplicationIds.stream()
            .map(a -> new DiameterNodeConfig.VendorSpecificApp(VENDOR_ID_3GPP, a))
            .toList();
    }

    private void connect(final DiameterNodeConfig nodeConfig, final DiameterPeer peer) {
        // Resource transfer pattern: if connect() succeeds, ownership moves to `this.nodes`.
        // Nulling `newNode` prevents the finally-block from closing the transferred resource.
        var newNode = new DiameterNode(meterRegistry);
        try {
            newNode.connect(
                peer.host(),
                peer.port(),
                reconnect -> mkDiameterInitiatorSession(nodeConfig, peer, reconnect));

            LOGGER.info("Diameter session connecting to {}:{}", peer.host(), peer.port());
            nodes.add(newNode);
            newNode = null;
        } finally {
            if (newNode != null) {
                newNode.close();
            }
        }
    }

    private @NonNull DiameterInitiatorSession mkDiameterInitiatorSession(
        final DiameterNodeConfig nodeConfig,
        final DiameterPeer peer,
        final Consumer<DiameterInitiatorSession> reconnect) {
        try {
            final var oldSession = diameterSessions.remove(peer);
            if (oldSession != null) {
                oldSession.stopGracefully();
            }
        } catch (final Exception e) {
            LOGGER.error("unhandled exception while stopping old session", e);
        }

        final var newSession = new DiameterInitiatorSession(nodeConfig, reconnect, meterRegistry);
        registerHandlers(newSession);
        diameterSessions.register(peer, newSession);
        return newSession;
    }

    /// Package-private so tests can verify the dispatch guard on every registration.
    ///
    /// Dispatch keys on the concrete `.In` class, so interfaces that reuse command codes (SWx
    /// reuses the Cx/Dx codes with distinct `.In` classes) register independently.
    void registerHandlers(final DiameterInitiatorSession session) {
        for (final var handler : handlers) {
            register(session, handler);
        }
    }

    private <R extends IncomingRequest<A>, A extends OutgoingAnswer> void register(
        final DiameterInitiatorSession session, final RegisterableDiameterHandler<R, A> handler) {
        session.setHandler(handler.requestType(), wrap(handler));
    }

    /// Wraps a handler so it runs on [#inboundExecutor] instead of the Netty event loop. The event
    /// loop also drives the DWR/DWA watchdog and every other request on a peer connection, so a
    /// handler's blocking work (DB, event listeners, external authorization calls) must not run there.
    ///
    /// `@Transactional` stays correct: the hosting container proxies the handler bean, so the
    /// transaction advice runs on the worker thread that invokes it.
    ///
    /// Robustness against the lib contract (DiameterSession.dispatchInboundRequest): only an
    /// exceptionally completed future becomes an UNABLE_TO_COMPLY (5012) answer. `supplyAsync` turns
    /// a synchronous handler throw into one; a saturated pool would otherwise reject synchronously on
    /// the event loop (→ Netty exceptionCaught → connection close), so the rejection is caught here;
    /// null futures/answers (which would vanish answerless in the send path) become failed futures.
    private
        <R extends IncomingRequest<A>, A extends OutgoingAnswer>
        DiameterRequestHandler<R, A>
        wrap(final DiameterRequestHandler<R, A> handler) {
        return request -> {
            final var timerSample = Timer.start();
            final var cmdName = request.getCommandName();
            final var userName = userNameOf(request);
            final var sid = request.getSessionId();
            final var appid = request.getApplicationId();

            final var timerMetric = Timer
                .builder("diameter_request_duration_millis")
                .serviceLevelObjectives(Arrays.stream(HIST_BARS_BUCKET_MILLIS).mapToObj(Duration::ofMillis).toArray(Duration[]::new))
                .tags("request_type", cmdName, "app_id", String.valueOf(appid))
                .register(meterRegistry);
            LOGGER.trace(">>>[WRAP in] imsi={} cmd={}/{} s={}", userName, appid, cmdName, sid);

            // the supplier returns a CompletableFuture, so supplyAsync yields a nested future that
            // the following thenCompose flattens back to CompletableFuture<A>
            final CompletableFuture<CompletableFuture<A>> dispatched;
            try {
                dispatched = CompletableFuture.supplyAsync(() -> handler.handle(request), inboundExecutor);
            } catch (final RejectedExecutionException e) {
                LOGGER.warn("imsi={} inbound executor rejected {} from {} under load", userName, cmdName, originOf(request));
                return CompletableFuture.failedFuture(e);
            }
            return dispatched
                .thenCompose(future -> {
                    if (future == null) {
                        return CompletableFuture.<A>failedFuture(new IllegalStateException("handler for " + cmdName + " returned a null future"));
                    }
                    return future;
                })
                .thenApply(answer -> {
                    if (answer == null) {
                        throw new IllegalStateException("handler for " + cmdName + " completed with a null answer");
                    }
                    return answer;
                })
                .whenComplete((answer, error) -> {
                    timerSample.stop(timerMetric);
                    final var mccMnc = MncMccFormatter.originRealmToMccMnc(request.getOriginRealm());
                    meterRegistry.counter("diameter_request_result",
                            "request_type", cmdName,
                            "app_id", String.valueOf(appid),
                            "result_code", resultCodeOf(answer, error),
                            "mcc_origin_realm", mccMnc != null ? mccMnc.mcc() : "unknown",
                            "mnc_origin_realm", mccMnc != null ? mccMnc.mnc() : "unknown")
                        .increment();

                    if (error != null) {
                        if (error.getCause() instanceof DiameterErrorAnswerException) {
                            LOGGER.info("imsi={} handler for {} from {} returned business error. Returning answer code: {}", userName, cmdName, originOf(request), resultCodeOf(answer, error.getCause()));
                        } else {
                            LOGGER.warn("imsi={} handler for {} from {} error. Returning answer code: {}", userName, cmdName, originOf(request), resultCodeOf(answer, error.getCause()), error.getCause());
                        }
                    }
                    LOGGER.trace("<<<[WRAP out] imsi={} cmd={}/{} result={} s={}", userName, appid, cmdName, resultCodeOf(answer, error), sid);
                });
        };
    }

    /// 2001 → `diameter_success`; standard error → `rc_<code>`; SWx/Cx 3GPP errors in
    /// Experimental-Result → `experimental_<code>`; no answer (lib sends 5012) → `5012_unable_to_comply`.
    private static String resultCodeOf(final OutgoingAnswer answer, final Throwable error) {
        if (error != null) {
            if (error instanceof final DiameterErrorAnswerException diamErr && diamErr.getAnswer() != null) {
                return getResultCodeFromAnswer(diamErr.getAnswer());
            }
            return "5012_unable_to_comply";
        }
        return getResultCodeFromAnswer(answer);
    }

    private static @Nullable String getResultCodeFromAnswer(final HasResultCodeAVP answer) {
        if (answer instanceof final HasExperimentalResultAVP expAnswer) {
            final var exp = expAnswer.getExperimentalResult();
            if (exp != null) {
                final var codeAvp = exp.findAVP(new AVPKey(DiameterConstants.AVP_EXPERIMENTAL_RESULT_CODE, 0));
                if (codeAvp != null) {
                    return "experimental_" + codeAvp.getDataAsUnsignedInt();
                }
            }
        }

        if (answer == null) {
            return "no_result_code";
        }

        final var resultCode = answer.getResultCode();
        if (resultCode == 2001L) {
            return "diameter_success";
        }

        return "rc_" + resultCode;
    }

    private static String originOf(final IncomingRequest<?> request) {
        return request instanceof final HasOriginHostAVP withOrigin ? withOrigin.getOriginHost() : "<unknown>";
    }

    private static String userNameOf(final IncomingRequest<?> request) {
        return request instanceof final HasUserNameAVP withUserName ? withUserName.getUserName() : "<none>";
    }

    Map<DiameterPeer, PeerState> getPeerStates() {
        return diameterSessions.getPeerStates();
    }

    public void close() {
        LOGGER.info("Stopping Diameter Connections for {} peers", peers.size());

        try {
            for (final var peer : peers) {
                final var session = diameterSessions.remove(peer);
                if (session != null) {
                    session.stopGracefully();
                }
            }
        } finally {
            nodes.forEach(DiameterNode::close);
        }
    }
}
