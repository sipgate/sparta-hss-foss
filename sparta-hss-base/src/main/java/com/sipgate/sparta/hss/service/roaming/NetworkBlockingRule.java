package com.sipgate.sparta.hss.service.roaming;

import java.util.List;

public record NetworkBlockingRule(
    List<String> mccMnc,
    List<String> gtPrefix,
    String reason
) {
    public NetworkBlockingRule {
        mccMnc.forEach(NetworkBlockingRule::validateMccMnc);
    }

    static void validateMccMnc(final String mccMnc) {
        final String[] parts = mccMnc.split("-", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid mcc-mnc format (expected 'MCC-MNC'): " + mccMnc);
        }
        if (!parts[0].matches("\\d{3}")) {
            throw new IllegalArgumentException("Invalid MCC (expected exactly 3 digits): " + parts[0]);
        }
        if (!parts[1].matches("\\d{2,3}")) {
            throw new IllegalArgumentException("Invalid MNC (expected 2 or 3 digits): " + parts[1]);
        }
    }
}
