package com.sipgate.sparta.hss.service.roaming;

import java.util.List;

public record ImsiRule(
    List<String> mccMnc,
    List<String> gtPrefix,
    Roaming roaming,
    String reason
) {
    public ImsiRule {
        mccMnc.forEach(NetworkBlockingRule::validateMccMnc);
    }

    public enum Roaming {
        BLOCKED,
        ALLOWED,
        DEFAULT,
    }
}
