package com.sipgate.sparta.hss.diameter.client;

import com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants;
import com.sipgate.sparta.diameter._3gpp.s6a.S6aConstants;
import com.sipgate.sparta.diameter._3gpp.swx.SwxConstants;
import jakarta.validation.constraints.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "sipgate.diameter")
public record DiameterConfig(
    @NotBlank String originHost,
    @NotBlank String originRealm,
    @NotEmpty List<Peer> peers,
    @DefaultValue("PT30S") Duration reconnectDelay,
    @DefaultValue("PT30S") Duration watchdogInterval,
    @Nullable String hostIp,
    @DefaultValue({"Cx/Dx", "S6a/S6d"}) List<String> capabilities
) {
    public static final String CAPABILITY_CX_DX = "Cx/Dx";
    public static final String CAPABILITY_S6A_S6D = "S6a/S6d";
    public static final String CAPABILITY_SWX = "SWx";

    /// Must match the `@DefaultValue` on [#capabilities()].
    public static final List<String> DEFAULT_CAPABILITIES = List.of(CAPABILITY_CX_DX, CAPABILITY_S6A_S6D);

  @Validated
  public record Peer(
    @NotBlank String host,
    @DefaultValue("3868") int port
  ) {}

    public List<Long> capabilitiesAsLongs() {
      final var capabilities = new ArrayList<Long>();
      for (final var cap : this.capabilities) {
          switch (cap.toLowerCase()) {
              case "cx/dx" -> capabilities.add((long)CxDxConstants.APP_ID_CX_DX);
              case "s6a/s6d" -> capabilities.add((long)S6aConstants.APP_ID_S6A_S6D);
              case "swx" -> capabilities.add((long)SwxConstants.APP_ID_SWX);
              default -> throw new IllegalArgumentException("Unknown capability: " + cap);
          }
      }
      return capabilities;
    }
}
