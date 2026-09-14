package com.sipgate.sparta.hss.diameter.common.auth;

/// An AKA authentication vector as produced by an [Authenticator], stripped of the
/// technology-specific KASME derivation. The first six bytes of AUTN are SQN&#8853;AK
/// (3GPP TS 33.102 §6.3.3: AUTN = SQN&#8853;AK[0..5] &#8214; AMF[6..7] &#8214; MAC-A[8..15], 16 bytes total),
/// exposed separately because SWx/S6a need the concealed SQN without re-parsing AUTN.
///
/// @param rand     the random challenge (16 bytes, TS 33.102 §6.2)
/// @param autn      the network authentication token (16 bytes, TS 33.102 §6.3.3)
/// @param xres      the expected response (Milenage f2, TS 35.206)
/// @param ck        the confidentiality key (16 bytes, Milenage f3, TS 35.206)
/// @param ik        the integrity key (16 bytes, Milenage f4, TS 35.206)
/// @param sqnXorAk  AUTN[0..5] = the concealed SQN (SQN&#8853;AK, AK = Milenage f5)
public record AkaVector(
        byte[] rand,
        byte[] autn,
        byte[] xres,
        byte[] ck,
        byte[] ik,
        byte[] sqnXorAk
) {}
