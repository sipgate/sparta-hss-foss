package com.sipgate.sparta.hss.diameter.s6a.ulr;

import java.util.Optional;

/// Published whenever a subscriber attaches at a visited PLMN. The IMEI is only present when the
/// ULR carried Terminal-Information; consumers that need it must handle its absence.
public record LocationUpdated(String mcc, String mnc, String imsi, String msisdn, Optional<String> imei) {

    private static final int TAC_LENGTH = 8;

    /// The Type Allocation Code, i.e. the leading digits of the IMEI identifying the device model.
    public Optional<String> tac() {
        return imei.map(value -> value.length() < TAC_LENGTH ? value : value.substring(0, TAC_LENGTH));
    }
}
