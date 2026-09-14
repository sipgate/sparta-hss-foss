package com.sipgate.sparta.hss.diameter.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.sipgate.sparta.hss.diameter.common.Factory;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class DiameterSessionsTest {

    private static final String ORIGIN_HOST = "hss.epc.mnc999.mcc999.3gppnetwork.org";
    private static final long UNSIGNED_32_BIT_MAX = 0xFFFFFFFFL;

    private final DiameterSessions underTest =
            new DiameterSessions(Factory.nodeConfig(ORIGIN_HOST, "epc.mnc999.mcc999.3gppnetwork.org"));

    @Test
    void itGeneratesSessionIdsInRfc6733Format() {
        // WHEN
        final var sessionId = underTest.nextSessionId();

        // THEN: <DiameterIdentity>;<high 32 bits>;<low 32 bits> (RFC 6733 §8.8)
        assertThat(sessionId).matches("\\Q" + ORIGIN_HOST + "\\E;\\d+;\\d+");

        final var parts = sessionId.substring(ORIGIN_HOST.length() + 1).split(";");
        assertThat(Long.parseLong(parts[0])).isBetween(0L, UNSIGNED_32_BIT_MAX);
        assertThat(Long.parseLong(parts[1])).isBetween(0L, UNSIGNED_32_BIT_MAX);
    }

    @Test
    void itGeneratesUniqueSessionIdsAcrossCalls() {
        // WHEN
        final var sessionIds = new HashSet<String>();
        for (var i = 0; i < 10_000; i++) {
            sessionIds.add(underTest.nextSessionId());
        }

        // THEN
        assertThat(sessionIds).hasSize(10_000);
    }
}
