package com.sipgate.e2e.agent;

import com.sipgate.sparta.diameter._3gpp.common._3gppConstants;
import com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants;
import com.sipgate.sparta.diameter._3gpp.cxdx.messages.RegistrationTerminationRequest;
import com.sipgate.sparta.diameter._3gpp.s6a.S6aConstants;
import com.sipgate.sparta.diameter._3gpp.s6a.messages.CancelLocationRequest;
import com.sipgate.sparta.diameter._3gpp.s6a.messages.InsertSubscriberDataRequest;
import com.sipgate.sparta.diameter.base.core.Answer;
import com.sipgate.sparta.diameter.base.core.DiameterConstants;
import com.sipgate.sparta.diameter.base.core.DiameterMessageFactory;
import com.sipgate.sparta.diameter.base.core.IncomingRequest;
import com.sipgate.sparta.diameter.base.core.OutgoingAnswer;
import com.sipgate.sparta.diameter.base.core.OutgoingRequest;
import com.sipgate.sparta.diameter.base.session.DiameterNodeConfig;
import com.sipgate.sparta.diameter.base.session.DiameterRequestHandler;
import com.sipgate.sparta.diameter.base.session.DiameterResponderSession;
import com.sipgate.sparta.diameter.base.session.PeerState;
import com.sipgate.sparta.diameter.base.transport.DiameterNode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.awaitility.Awaitility;

public class DiameterTestAgent {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final DiameterNode node = new DiameterNode(meterRegistry);
    private final DiameterNodeConfig config;
    private final AtomicReference<DiameterResponderSession> session = new AtomicReference<>();
    private final Map<Class<?>, BlockingQueue<IncomingRequest<?>>> captured = new ConcurrentHashMap<>();
    private final Map<Class<?>, Function<?, ?>> responders = new ConcurrentHashMap<>();
    private volatile int boundPort;

    public DiameterTestAgent(final String originHost, final String originRealm) {
        this.config = new DiameterNodeConfig(
            originHost,
            originRealm,
            List.of(InetAddress.getLoopbackAddress()),
            0,
            "sparta-hss-e2e-agent",
            new DiameterNodeConfig.Capabilities(
                List.of(),
                List.of(),
                List.of((long) _3gppConstants.VENDOR_ID_3GPP),
                List.of(
                    new DiameterNodeConfig.VendorSpecificApp(
                        _3gppConstants.VENDOR_ID_3GPP, S6aConstants.APP_ID_S6A_S6D),
                    new DiameterNodeConfig.VendorSpecificApp(
                        _3gppConstants.VENDOR_ID_3GPP, CxDxConstants.APP_ID_CX_DX))));
    }

    public DiameterTestAgent start(final int port) {
        final var future = node.listen(port, () -> {
            final var newSession = new DiameterResponderSession(config, meterRegistry);
            registerCapture(newSession, CancelLocationRequest.In.class);
            registerCapture(newSession, InsertSubscriberDataRequest.In.class);
            registerCapture(newSession, RegistrationTerminationRequest.In.class);
            session.set(newSession);
            return newSession;
        }).syncUninterruptibly();
        boundPort = ((InetSocketAddress) future.channel().localAddress()).getPort();
        return this;
    }

    public int port() {
        return boundPort;
    }

    public PeerState peerState() {
        final var current = session.get();
        return current == null ? PeerState.CLOSED : current.getPeerState();
    }

    public void awaitConnected(final Duration timeout) {
        Awaitility.await()
            .atMost(timeout)
            .pollInterval(Duration.ofMillis(200))
            .until(() -> peerState() == PeerState.R_OPEN);
    }

    public <A extends Answer> A sendAndWait(final OutgoingRequest<A> req, final Duration timeout) {
        try {
            return send(req).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (final Exception e) {
            throw new RuntimeException("request " + req.getClass().getName() + " failed", e);
        }
    }

    public <A extends Answer> CompletableFuture<A> send(final OutgoingRequest<A> req) {
        final var current = session.get();
        if (current == null || current.getPeerState() != PeerState.R_OPEN) {
            throw new IllegalStateException("no connected peer (state=" + peerState() + ")");
        }
        try {
            return current.send(req);
        } catch (final Exception e) {
            throw new RuntimeException("request " + req.getClass().getName() + " failed", e);
        }
    }

    public <R extends IncomingRequest<?>> R awaitRequest(final Class<R> type, final Duration timeout) {
        return pollRequest(type, timeout)
            .orElseThrow(() -> new AssertionError("no " + type.getSimpleName() + " received within " + timeout));
    }

    /// Same as {@link #awaitRequest} but empty instead of failing - for asserting that a request is *not* sent.
    @SuppressWarnings("unchecked")
    public <R extends IncomingRequest<?>> Optional<R> pollRequest(final Class<R> type, final Duration timeout) {
        try {
            return Optional.ofNullable((R) queueFor(type).poll(timeout.toMillis(), TimeUnit.MILLISECONDS));
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    public void reset() {
        captured.values().forEach(BlockingQueue::clear);
        responders.clear();
    }

    private BlockingQueue<IncomingRequest<?>> queueFor(final Class<?> type) {
        return captured.computeIfAbsent(type, ignored -> new LinkedBlockingQueue<>());
    }

    @SuppressWarnings("unchecked")
    private <R extends IncomingRequest<A>, A extends OutgoingAnswer> void registerCapture(
        final DiameterResponderSession target, final Class<R> type) {
        final DiameterRequestHandler<R, A> handler = request -> {
            queueFor(type).add(request);
            final var responder = (Function<R, A>) responders.get(type);
            final var answer = responder != null
                ? responder.apply(request)
                : DiameterMessageFactory.createAnswer(request, DiameterConstants.RES_DIAMETER_SUCCESS);
            return CompletableFuture.completedFuture(answer);
        };
        target.setHandler(type, handler);
    }

    void close() {
        node.close();
    }
}
