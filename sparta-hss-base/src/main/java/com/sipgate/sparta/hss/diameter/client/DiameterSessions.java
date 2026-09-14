package com.sipgate.sparta.hss.diameter.client;

import com.sipgate.sparta.diameter.base.core.Answer;
import com.sipgate.sparta.diameter.base.core.DiameterConstants;
import com.sipgate.sparta.diameter.base.core.OutgoingRequest;
import com.sipgate.sparta.diameter.base.core.Request;
import com.sipgate.sparta.diameter.base.core.avp.AVP;
import com.sipgate.sparta.diameter.base.core.avp.AVPKey;
import com.sipgate.sparta.diameter.base.core.avp.mixins.HasAuthSessionStateAVP;
import com.sipgate.sparta.diameter.base.core.avp.mixins.HasVendorSpecificApplicationIdAVP;
import com.sipgate.sparta.diameter.base.session.DiameterInitiatorSession;
import com.sipgate.sparta.diameter.base.session.DiameterNodeConfig;
import com.sipgate.sparta.diameter.base.session.PeerState;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Holds the currently established initiator sessions to the Diameter Routing Agent and lets the
/// application send outbound requests (CLR, RTR, ISD).
///
/// Kept separate from [DiameterConnectionHandler] so that request handlers can depend on it
/// to send outbound requests while the connection handler depends on the handlers to register them —
/// without a circular bean dependency. Outbound requests are routed by the DRA based on their
/// Destination-Host/Destination-Realm, so any open session may be used to send them.
public class DiameterSessions {

  private static final Logger LOGGER = LoggerFactory.getLogger(DiameterSessions.class);

  /// Session-Ids must stay within 32 bits per field (RFC 6733 §8.8).
  private static final long UNSIGNED_32_BIT_MASK = 0xFFFFFFFFL;

  private final Map<DiameterPeer, DiameterInitiatorSession> sessions = new ConcurrentHashMap<>();

  private final String originHost;
  private final String originRealm;
  private final long sessionIdHigh;
  private final AtomicLong sessionIdLow =
      new AtomicLong(ThreadLocalRandom.current().nextLong() & UNSIGNED_32_BIT_MASK);

  public DiameterSessions(final DiameterNodeConfig config) {
    this.originHost = config.getOriginHost();
    this.originRealm = config.getOriginRealm();
    this.sessionIdHigh = Instant.now().getEpochSecond() & UNSIGNED_32_BIT_MASK;
  }

  /// Generates a globally and eternally unique Session-Id per RFC 6733 §8.8:
  /// `<DiameterIdentity>;<high 32 bits>;<low 32 bits>`. The high part is the startup
  /// timestamp in epoch-seconds, the low part a monotonic counter starting at a random 32-bit
  /// value to reduce overlap risk when multiple nodes start within the same second.
  public String nextSessionId() {
    return originHost + ";" + sessionIdHigh + ";" + (sessionIdLow.getAndIncrement() & UNSIGNED_32_BIT_MASK);
  }

  void register(final DiameterPeer peer, final DiameterInitiatorSession session) {
    sessions.put(peer, session);
  }

  DiameterInitiatorSession remove(final DiameterPeer peer) {
    return sessions.remove(peer);
  }

  /// Snapshot of the peer states, keyed by configured peer.
  public Map<DiameterPeer, PeerState> getPeerStates() {
    final var peerStates = new HashMap<DiameterPeer, PeerState>();
    for (final var entry : sessions.entrySet()) {
      peerStates.put(entry.getKey(), entry.getValue().getPeerState());
    }
    return peerStates;
  }

  /// Sends an outbound request over any open session and returns a future that completes with the
  /// answer. The future completes exceptionally when no peer connection is currently open.
  public <A extends Answer> CompletableFuture<A> send(final OutgoingRequest<A> request) {

    // TODO: Round Robin?
    for (final var session : sessions.values()) {
      final var state = session.getPeerState();
      if (state == PeerState.I_OPEN || state == PeerState.R_OPEN) {
        return session.send(request);
      }
    }
    LOGGER.error("cannot send outbound {}: no open Diameter peer connection", request.getClass().getSimpleName());
    final var failed = new CompletableFuture<A>();
    failed.completeExceptionally(new IllegalStateException("no open Diameter peer connection"));
    return failed;
  }

  public <R extends Request<? extends Answer>> R createRequest(
      final Class<R> requestClass,
      final int authSessionState,
      final String destinationHost,
      final String destinationRealm,
      final int vendorSpecificVendorId,
      final int vendorSpecificAuthAppId)
  {

      try {
          final var r = requestClass.getDeclaredConstructor().newInstance();

          r.setSessionId(nextSessionId());

          if (r instanceof final HasVendorSpecificApplicationIdAVP with) {
              with.setVendorSpecificApplicationId(List.of(
                  AVP.create(new AVPKey(DiameterConstants.AVP_VENDOR_ID, 0), (long) vendorSpecificVendorId),
                  AVP.create(new AVPKey(DiameterConstants.AVP_AUTH_APPLICATION_ID, 0), (long) vendorSpecificAuthAppId)
              ));
          }
          if (r instanceof final HasAuthSessionStateAVP with) {
              with.setAuthSessionState(authSessionState);
          }

          r.setOriginHost(originHost);
          r.setOriginRealm(originRealm);
          r.setDestinationHost(destinationHost);
          r.setDestinationRealm(destinationRealm);

          return r;
      } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
          throw new RuntimeException(e);
      }
  }
}
