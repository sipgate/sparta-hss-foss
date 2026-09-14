package com.sipgate.sparta.hss.diameter.cx.sar.identity;

import com.sipgate.sparta.hss.diameter.cx.sar.service.SimService;
import java.util.Objects;

public class ImsiPublicIdentity extends PublicIdentity {

    private static final int IMSI_LEN = 15;
    private static final String PREFIX = "sip:";
    private final String imsi;
    private final String msisdn;

    ImsiPublicIdentity(final String publicIdentity, final SimService simService) {
        super(simService);
        this.imsi = publicIdentity.substring(PREFIX.length(), PREFIX.length() + IMSI_LEN);
        this.msisdn = simService.getMsisdnByImsi(imsi).orElse(null);
    }

    static boolean isParsable(final String publicIdentity) {
        return publicIdentity.startsWith(PREFIX);
    }

    @Override
    public boolean isKnown() {
        return msisdn != null;
    }

    @Override
    public boolean isAssociatedWithImsi(final String imsi) {
        return Objects.equals(this.imsi, imsi);
    }

    @Override
    public String getMsisdn() {
        return msisdn;
    }
}
