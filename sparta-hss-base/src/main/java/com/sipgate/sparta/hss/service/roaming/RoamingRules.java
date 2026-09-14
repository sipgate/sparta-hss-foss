package com.sipgate.sparta.hss.service.roaming;

import java.util.List;
import java.util.Map;

public record RoamingRules(
    List<NetworkBlockingRule> networks,
    Map<String, ImsiRule> imsis
) {
    public RoamingRules {
        imsis.keySet().forEach(RoamingRules::validateImsi);
    }

    private static void validateImsi(final String imsi) {
        if (!imsi.matches("\\d{13,15}")) {
            throw new IllegalArgumentException("Invalid IMSI (expected 13 to 15 digits): " + imsi);
        }
    }
}
