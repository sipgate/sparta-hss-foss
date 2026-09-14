package com.sipgate.sparta.hss.service.roaming;

import java.util.Set;

public class EmergencyRoamingRestriction {

    public static final Set<String> ZONE_ONE_MCCS = Set.of(
        "202", // Greece
        "204", // Netherlands
        "206", // Belgium
        "208", // France
        "214", // Spain
        "216", // Hungary
        "219", // Croatia
        "222", // Italy
        "226", // Romania
        "230", // Czechia
        "231", // Slovakia
        "232", // Austria
        "234", // United Kingdom
        "238", // Denmark
        "240", // Sweden
        "242", // Norway
        "244", // Finland
        "246", // Lithuania
        "247", // Latvia
        "248", // Estonia
        "260", // Poland
        "262", // Germany
        "268", // Portugal
        "270", // Luxembourg
        "272", // Ireland
        "274", // Iceland
        "278", // Malta
        "280", // Cyprus
        "284", // Bulgaria
        "293", // Slovenia
        "295"  // Liechtenstein
    );

    private final boolean enabled;
    private final Set<String> allowedMccs;

    public EmergencyRoamingRestriction(final boolean enabled, final Set<String> allowedMccs) {
        this.enabled = enabled;
        this.allowedMccs = Set.copyOf(allowedMccs);
    }

    public boolean blocksMcc(final String mcc) {
        return enabled && !allowedMccs.contains(mcc);
    }
}
