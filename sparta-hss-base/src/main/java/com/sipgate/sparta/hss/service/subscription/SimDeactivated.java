package com.sipgate.sparta.hss.service.subscription;

import java.util.List;

/// Published by [SubscriptionStateService#deactivate] when a SIM is deactivated. Carries only the
/// subscriber's identity — the IMSIs of the SIM and its MSISDN. It deliberately carries no
/// per-interface details: each outbound listener fetches what it needs from its own table and
/// owns that table's lifecycle (fetch, send, remove).
///
/// @param msisdn the SIM's MSISDN, without `+` (the Cx Public Identity is built from it)
/// @param imsis  the IMSIs of the SIM
public record SimDeactivated(String iccid, String msisdn, List<String> imsis) {

    public SimDeactivated {
        imsis = List.copyOf(imsis);
    }
}
