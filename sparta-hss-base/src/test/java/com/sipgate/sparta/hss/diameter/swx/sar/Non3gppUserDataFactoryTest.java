package com.sipgate.sparta.hss.diameter.swx.sar;

import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.AVP_APN_CONFIGURATION;
import static org.assertj.core.api.Assertions.assertThat;

import com.sipgate.sparta.diameter.base.core.avp.AVP;
import com.sipgate.sparta.hss.diameter.s6a.common.SubscriptionDataEncoder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class Non3gppUserDataFactoryTest {

    // Same working-dir-relative path the S6a SubscriptionDataFactoryTest uses → loads the real profiles.
    private Non3gppUserDataFactory factory() {
        final var f = new Non3gppUserDataFactory(
            "src/test/resources/imsiProfiles", new SimpleMeterRegistry(),
            new Non3gppUserDataEncoder(new SubscriptionDataEncoder()));
        f.init();
        return f;
    }

    @Test
    void itReturnsTheDefaultProfileWithAtLeastOneApnConfiguration() {
        // WHEN
        final List<AVP> avps = factory().createNon3gppUserData("default", null);
        // THEN — non-empty and contains the mandatory APN-Configuration
        assertThat(avps).isNotEmpty();
        assertThat(avps.stream().anyMatch(a -> a.getCode() == (long) AVP_APN_CONFIGURATION)).isTrue();
    }

    @Test
    void itOmitsSubscriptionIdSoMsisdnDoesNotChangeTheResult() {
        // GIVEN/WHEN — Subscription-Id(443) is unregistered in the lib, so MSISDN is not emitted
        final var withMsisdn = factory().createNon3gppUserData("default", "9999999912345");
        final var without = factory().createNon3gppUserData("default", null);
        // THEN
        assertThat(withMsisdn).hasSameSizeAs(without);
    }
}
