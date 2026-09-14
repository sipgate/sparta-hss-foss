package com.sipgate.sparta.hss.diameter.client;

/// A Diameter peer to connect to. Carries the unresolved hostname so DNS resolution happens
/// inside the transport on every (re)connect — the peer may not be resolvable when the HSS
/// boots (e.g. containers starting in parallel) and its address may change between reconnects.
public record DiameterPeer(String host, int port) {
}
