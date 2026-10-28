package com.sipgate.sparta.hss.service.accessPolicy;

import java.util.Optional;

public class AllowAllAccessPolicy implements AccessPolicy {
    @Override
    public Optional<Boolean> isEutranAccessAllowed(final String imsi, final String mcc) {
        return Optional.of(true);
    }

    @Override
    public Optional<Boolean> isImsAccessAllowed(final String imsi) {
        return Optional.of(true);
    }
}
