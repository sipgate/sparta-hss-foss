package com.sipgate.sparta.hss.http.roaming.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sipgate.sparta.hss.service.roaming.NetworkBlockingRule;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NetworkBlockingRuleDto(
    @JsonProperty("mcc-mnc") List<String> mccMnc,
    @JsonProperty("gt-prefix") List<String> gtPrefix,
    String reason
) {
    NetworkBlockingRule toDomain() {
        return new NetworkBlockingRule(mccMnc, gtPrefix, reason);
    }
}
