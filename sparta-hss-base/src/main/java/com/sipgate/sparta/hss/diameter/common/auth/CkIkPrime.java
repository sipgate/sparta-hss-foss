package com.sipgate.sparta.hss.diameter.common.auth;

/**
 * Holds the EAP-AKA' derived keys CK' and IK' (16 bytes each).
 *
 * @param ckPrime CK' = KDF[0..127]  — first 16 bytes of the HMAC-SHA256 output
 * @param ikPrime IK' = KDF[128..255] — last 16 bytes of the HMAC-SHA256 output
 *         (3GPP TS 33.402 Annex A; RFC 9048 §3.3)
 */
public record CkIkPrime(byte[] ckPrime, byte[] ikPrime) {

    public CkIkPrime {
        ckPrime = ckPrime.clone();
        ikPrime = ikPrime.clone();
    }
}
