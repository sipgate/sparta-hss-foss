package com.sipgate.sparta.hss.diameter.s6a.ulr;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class LocationUpdatedTest {

    @Test
    void tacIsTheFirstEightDigitsOfTheImei() {
        final var event = new LocationUpdated("262", "07", "imsi", "msisdn", Optional.of("1234567809"));

        assertThat(event.tac()).contains("12345678");
    }

    @Test
    void tacIsTheWholeImeiWhenShorterThanEightDigits() {
        final var event = new LocationUpdated("262", "07", "imsi", "msisdn", Optional.of("1234567"));

        assertThat(event.tac()).contains("1234567");
    }

    @Test
    void tacIsAbsentWithoutImei() {
        final var event = new LocationUpdated("262", "07", "imsi", "msisdn", Optional.empty());

        assertThat(event.tac()).isEmpty();
    }
}
