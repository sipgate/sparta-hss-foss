package com.sipgate.sparta.hss.diameter.common.auth;

import java.security.SecureRandom;

/// Source of the AKA random challenge RAND. 3GPP TS 33.102 §6.2 requires RAND to be unpredictable
/// to the peer; therefore a cryptographically strong RNG is mandatory. java.util.Random is a
/// predictable 48-bit LCG and MUST NOT be used for the challenge.
public class RandomGenerator {

    private final SecureRandom random = new SecureRandom();

    public byte[] nextRand(final int size) {
        final var result = new byte[size];
        random.nextBytes(result);
        return result;
    }
}
