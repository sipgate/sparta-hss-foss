package com.sipgate.sparta.hss.diameter.s6a.air;

/// Decides whether a subscriber may access E-UTRAN (4G) in a visited network. The
/// [AuthenticationInfoHandler] consults this before issuing E-UTRAN authentication vectors:
/// a denied subscriber cannot authenticate for 4G in that network.
///
/// The hosting application can plug in an external decision system (e.g. a charging or roaming
/// steering gateway). By default every subscriber is allowed.
@FunctionalInterface
public interface EutranAccessPolicy {

    boolean isEutranAccessAllowed(String imsi, String mcc);

    static EutranAccessPolicy allowAll() {
        return (imsi, mcc) -> true;
    }
}
