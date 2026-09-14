package com.sipgate.sparta.hss.http.roaming.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sipgate.sparta.hss.service.roaming.RoamingRules;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/* example

{
  "networks":
    [
      { "mcc-mnc": ["260-123"], "gt-prefix": ["4917012345", "9876543"], "reason": "this is the thing" }
    ],
    "imsis": {
      "97347839393": { "mcc-mnc": ["260-123"], "gt-prefix": ["4917012345","9876543"], "roaming":"blocked", "reason": "this is the thing"
    }
  }
}

 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RoamingRulesDto(
    List<NetworkBlockingRuleDto> networks,
    Map<String, ImsiRuleDto> imsis
) {
    public RoamingRules toDomain() {
        return new RoamingRules(
            networks.stream().map(NetworkBlockingRuleDto::toDomain).toList(),
            imsis.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().toDomain())));
    }
}
