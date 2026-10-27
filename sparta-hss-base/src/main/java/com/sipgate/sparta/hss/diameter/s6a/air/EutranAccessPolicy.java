package com.sipgate.sparta.hss.diameter.s6a.air;

import java.util.Optional;

/// Decides whether a subscriber may access E-UTRAN (4G) in a visited network. The
/// [AuthenticationInfoHandler] consults this before issuing E-UTRAN authentication vectors:
/// a denied subscriber cannot authenticate for 4G in that network.
///
/// The hosting application can plug in an external decision system (e.g. a charging or roaming
/// steering gateway). By default every subscriber is allowed.
///
/// A return value of Optional.empty<>() indicates that no decision can be made for this IMSI, eg could not be found.
@FunctionalInterface
public interface EutranAccessPolicy {

    Optional<Boolean> isEutranAccessAllowed(String imsi, String mcc);

    static EutranAccessPolicy allowAll() {
        return (imsi, mcc) -> Optional.of(true);
    }
}
