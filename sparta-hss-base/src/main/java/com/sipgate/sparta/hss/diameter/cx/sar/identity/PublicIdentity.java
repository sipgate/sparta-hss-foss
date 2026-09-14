package com.sipgate.sparta.hss.diameter.cx.sar.identity;

import com.sipgate.sparta.hss.diameter.cx.sar.service.SimService;

public abstract class PublicIdentity {

    final SimService simService;

    PublicIdentity(final SimService simService) {
        this.simService = simService;
    }

    public static PublicIdentity parse(final String publicIdentity, final SimService simService) {
        if (ImsiPublicIdentity.isParsable(publicIdentity)) {
            return new ImsiPublicIdentity(publicIdentity, simService);
        }

        if (MsisdnPublicIdentity.isParsable(publicIdentity)) {
            return new MsisdnPublicIdentity(publicIdentity, simService);
        }

        return null;
    }

    public abstract boolean isKnown();

    public abstract boolean isAssociatedWithImsi(String imsi);

    public abstract String getMsisdn();
}
