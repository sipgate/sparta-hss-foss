package com.sipgate.sparta.hss.http.roaming.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sipgate.sparta.hss.service.roaming.ImsiRule;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ImsiRuleDto(
    @JsonProperty("mcc-mnc") List<String> mccMnc,
    @JsonProperty("gt-prefix") List<String> gtPrefix,
    Roaming roaming,
    String reason
) {
    ImsiRule toDomain() {
        return new ImsiRule(mccMnc, gtPrefix, roaming.toDomain(), reason);
    }

    public enum Roaming {
        @JsonProperty("blocked") BLOCKED,
        @JsonProperty("allowed") ALLOWED,
        @JsonProperty("default") DEFAULT,
        ;

        ImsiRule.Roaming toDomain() {
            return switch (this) {
                case BLOCKED -> ImsiRule.Roaming.BLOCKED;
                case ALLOWED -> ImsiRule.Roaming.ALLOWED;
                case DEFAULT -> ImsiRule.Roaming.DEFAULT;
            };
        }
    }
}
