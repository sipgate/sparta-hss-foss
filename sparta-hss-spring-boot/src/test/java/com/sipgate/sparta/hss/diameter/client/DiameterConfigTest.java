package com.sipgate.sparta.hss.diameter.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.sipgate.sparta.diameter._3gpp.swx.SwxConstants;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class DiameterConfigTest {

    private static final long APP_ID_SWX = SwxConstants.APP_ID_SWX;

    private static DiameterConfig configWith(final List<String> capabilities) {
        return new DiameterConfig(
            "hss.test.local",
            "test.local",
            List.of(new DiameterConfig.Peer("localhost", 3868)),
            Duration.ofSeconds(30),
            Duration.ofSeconds(30),
            null,
            capabilities);
    }

    @Test
    void itAdvertisesSwxAppIdWhenSwxIsConfigured() {
        // GIVEN
        final var config = configWith(List.of("Cx/Dx", "S6a/S6d", "Swx"));

        // WHEN
        final var capabilities = config.capabilitiesAsLongs();

        // THEN
        assertThat(capabilities).contains(APP_ID_SWX);
    }

    @Test
    void itDoesNotAdvertiseSwxAppIdByDefault() {
        // GIVEN
        final var config = configWith(List.of("Cx/Dx", "S6a/S6d"));

        // WHEN
        final var capabilities = config.capabilitiesAsLongs();

        // THEN
        assertThat(capabilities).doesNotContain(APP_ID_SWX);
    }

    /// Guards the DEFAULT_CAPABILITIES constant against the @DefaultValue literal: the
    /// capability condition falls back to the constant when the property is absent, so the two
    /// must describe the same default.
    @Test
    void theDefaultCapabilitiesConstantMatchesTheBoundDefault() {
        final var source = new MapConfigurationPropertySource(Map.of(
            "sipgate.diameter.origin-host", "hss.test.local",
            "sipgate.diameter.origin-realm", "test.local",
            "sipgate.diameter.peers[0].host", "localhost"));

        final var config = new Binder(source)
            .bind("sipgate.diameter", Bindable.of(DiameterConfig.class))
            .get();

        assertThat(config.capabilities()).isEqualTo(DiameterConfig.DEFAULT_CAPABILITIES);
    }
}
