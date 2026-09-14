package com.sipgate.sparta.hss.diameter.common.auth;

import java.util.Arrays;

/// The re-synchronisation payload of an AKA authentication request (3GPP TS 33.102 §6.3.5):
/// the RAND of the rejected challenge plus the AUTS token computed by the USIM. It arrives as
/// RAND || AUTS in the Re-Synchronization-Info AVP (S6a AIR) and the SIP-Authorization AVP (Cx MAR).
public record ResyncInfo(byte[] rand, byte[] auts) {

    private static final int RAND_LENGTH = 16;
    // TS 33.102 §6.3.5: AUTS = SQN_MS⊕AK* (6 B) ‖ MAC-S (8 B), 14 bytes total; produced by
    // the USIM on a sync failure. Decoded in MilenageAuthenticator.resyncSQN (f5*/f1*).
    private static final int AUTS_LENGTH = 14;

    public ResyncInfo {
        if (rand == null || rand.length != RAND_LENGTH) {
            throw new IllegalArgumentException("rand must be exactly " + RAND_LENGTH + " bytes");
        }
        if (auts == null || auts.length != AUTS_LENGTH) {
            throw new IllegalArgumentException("auts must be exactly " + AUTS_LENGTH + " bytes");
        }
    }

    /// Splits the concatenated AVP payload: the first 16 bytes are the RAND, everything after is the AUTS.
    public static ResyncInfo fromConcatenated(final byte[] randAndAuts) {
        if (randAndAuts == null) {
            return null;
        }
        if (randAndAuts.length < RAND_LENGTH + AUTS_LENGTH) {
            throw new IllegalArgumentException(
                "re-synchronisation info must be at least %d bytes (16-byte RAND + 14-byte AUTS), got %d bytes"
                    .formatted(RAND_LENGTH + AUTS_LENGTH, randAndAuts.length));
        }
        return new ResyncInfo(
            Arrays.copyOfRange(randAndAuts, 0, RAND_LENGTH),
            Arrays.copyOfRange(randAndAuts, RAND_LENGTH, randAndAuts.length));
    }


    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof ResyncInfo(final var otherRand, final var otherAuts))) return false;
        return Arrays.equals(rand, otherRand) && Arrays.equals(auts, otherAuts);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(rand) + Arrays.hashCode(auts);
    }

    @Override
    public String toString() {
        return "ResyncInfo[rand=" + Arrays.toString(rand) + ", auts=" + Arrays.toString(auts) + "]";
    }

}
