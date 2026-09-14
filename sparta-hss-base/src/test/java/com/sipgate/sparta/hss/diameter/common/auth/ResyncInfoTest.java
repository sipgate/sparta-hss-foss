package com.sipgate.sparta.hss.diameter.common.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class ResyncInfoTest {

    /// Returns {@code length} distinct bytes (0, 1, 2, ...) so the RAND/AUTS split is observable.
    private static byte[] countingBytes(final int length) {
        final var bytes = new byte[length];
        for (var i = 0; i < length; i++) {
            bytes[i] = (byte) i;
        }
        return bytes;
    }

    @Test
    void itPassesNullThrough() {
        assertThat(ResyncInfo.fromConcatenated(null)).isNull();
    }

    @Test
    void itRejectsInputShorterThanRandPlusAuts() {
        // minimum valid payload is 16-byte RAND + 14-byte AUTS = 30 bytes (TS 33.102 §6.3.5)
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ResyncInfo.fromConcatenated(countingBytes(29)));
    }

    @Test
    void itRejectsInputWithOnlyRandAndNoAuts() {
        // exactly 16 bytes — RAND present but AUTS missing entirely
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ResyncInfo.fromConcatenated(countingBytes(16)));
    }

    @Test
    void itRejectsInputWithRandAndPartialAuts() {
        // 17 bytes — AUTS is only 1 byte, not the required 14
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ResyncInfo.fromConcatenated(countingBytes(17)));
    }

    @Test
    void itSplits30BytesInto16ByteRandAnd14ByteAuts() {
        // 30 bytes is the real-world shape: 16-byte RAND plus 14-byte AUTS (TS 33.102 §6.3.5)
        final var resyncInfo = ResyncInfo.fromConcatenated(countingBytes(30));

        assertThat(resyncInfo.rand()).isEqualTo(countingBytes(16));
        assertThat(resyncInfo.auts())
                .containsExactly(16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29);
    }
}
