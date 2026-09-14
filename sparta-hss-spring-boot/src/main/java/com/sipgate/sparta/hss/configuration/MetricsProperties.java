package com.sipgate.sparta.hss.configuration;

import com.sipgate.sparta.hss.persistence.ImsiRange;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/// @param esimImsiRanges the operator's eSIM IMSI ranges; subscribers outside every range count
///                       as plastic SIMs in the roaming-location metric
@ConfigurationProperties(prefix = "sipgate.metrics")
public record MetricsProperties(
    @DefaultValue List<ImsiRange> esimImsiRanges
) {}
