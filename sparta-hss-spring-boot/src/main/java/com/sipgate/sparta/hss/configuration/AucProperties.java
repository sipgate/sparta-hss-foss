package com.sipgate.sparta.hss.configuration;

import com.sipgate.sparta.hss.diameter.common.auth.StoredKeyFormat;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/// @param storedKeyFormat whether the sim table's operator-key column holds the pre-computed
///                        OPc (the default and 3GPP TS 35.205 recommendation) or OP, from
///                        which OPc is then derived on every authentication
@ConfigurationProperties(prefix = "sipgate.auc")
public record AucProperties(
    @DefaultValue("OPC") StoredKeyFormat storedKeyFormat
) {}
