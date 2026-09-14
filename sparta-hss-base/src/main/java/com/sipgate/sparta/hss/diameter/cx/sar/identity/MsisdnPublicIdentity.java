package com.sipgate.sparta.hss.diameter.cx.sar.identity;

import com.sipgate.sparta.hss.diameter.cx.sar.service.SimService;
import java.util.Objects;

class MsisdnPublicIdentity extends PublicIdentity {

    private static final String PREFIX = "tel:+";
    private final String msisdn;

    MsisdnPublicIdentity(final String publicIdentity, final SimService simService) {
        super(simService);
        this.msisdn = publicIdentity.substring(PREFIX.length());
    }

    static boolean isParsable(final String publicIdentity) {
        return publicIdentity.startsWith(PREFIX);
    }

    @Override
    public boolean isKnown() {
        return simService.isMsisdnKnown(msisdn);
    }

    @Override
    public boolean isAssociatedWithImsi(final String imsi) {
        return simService.getMsisdnByImsi(imsi)
                .map(msisdnOfImsi -> Objects.equals(msisdnOfImsi, msisdn))
                .orElse(false);
    }

    @Override
    public String getMsisdn() {
        return msisdn;
    }
}
