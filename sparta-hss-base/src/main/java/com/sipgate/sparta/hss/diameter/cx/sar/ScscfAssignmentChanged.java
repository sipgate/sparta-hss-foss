package com.sipgate.sparta.hss.diameter.cx.sar;

import java.util.Objects;

/// Published when the S-CSCF assignment of a subscriber changes. A `null` [#scscfName()] means
/// the subscriber was deregistered.
public record ScscfAssignmentChanged(String imsi, String scscfName) {

    public static ScscfAssignmentChanged ofRegister(final String imsi, final String scscfName) {
        return new ScscfAssignmentChanged(imsi, Objects.requireNonNull(scscfName));
    }

    public static ScscfAssignmentChanged ofUnregister(final String imsi) {
        return new ScscfAssignmentChanged(imsi, null);
    }
}
