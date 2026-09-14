package com.sipgate.sparta.hss.diameter.swx.sar;

import java.util.Objects;

/// Published when the 3GPP-AAA-Server assignment of a subscriber changes. A `null`
/// [#aaaServerName()] means the subscriber was deregistered.
public record AaaServerAssignmentChanged(String imsi, String aaaServerName) {

    public static AaaServerAssignmentChanged ofRegister(final String imsi, final String aaaServerName) {
        return new AaaServerAssignmentChanged(imsi, Objects.requireNonNull(aaaServerName));
    }

    public static AaaServerAssignmentChanged ofUnregister(final String imsi) {
        return new AaaServerAssignmentChanged(imsi, null);
    }
}
