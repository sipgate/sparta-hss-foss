package com.sipgate.sparta.hss.service.roaming;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class EmergencyRoamingRestrictionTest {

    @Test
    void it_blocks_nothing_when_disabled() {
        final var underTest = new EmergencyRoamingRestriction(false, Set.of("262"));

        assertThat(underTest.blocksMcc("310")).isFalse();
        assertThat(underTest.blocksMcc("262")).isFalse();
    }

    @Test
    void it_blocks_mccs_outside_the_allowlist_when_enabled() {
        final var underTest = new EmergencyRoamingRestriction(true, Set.of("262", "204"));

        assertThat(underTest.blocksMcc("310")).as("US MCC must be blocked").isTrue();
        assertThat(underTest.blocksMcc("262")).as("allowlisted MCC must pass").isFalse();
        assertThat(underTest.blocksMcc("204")).as("allowlisted MCC must pass").isFalse();
    }

    @Test
    void eu_default_allowlist_contains_all_zone_one_mccs_and_no_others() {
        assertThat(EmergencyRoamingRestriction.ZONE_ONE_MCCS).containsExactlyInAnyOrder(

            // EU 27
            "202", "204", "206", "208", "214", "216", "219", "222", "226",
            "230", "231", "232", "238", "240", "244", "246", "247", "248",
            "260", "262", "268", "270", "272", "278", "280", "284", "293",

            // EWR (Norway, Liechtenstein, Iceland, UK)
            "234", "274", "295", "242"
            );
    }
}
