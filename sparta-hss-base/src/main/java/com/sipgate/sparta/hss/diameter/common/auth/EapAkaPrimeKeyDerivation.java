package com.sipgate.sparta.hss.diameter.common.auth;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/// Derives CK' and IK' per the EAP-AKA' key derivation function (RFC 9048 §3.3,
/// which delegates to the TS 33.220 KDF with `FC = 0x20` as specified in TS 33.402).
///
/// `CK' || IK' = HMAC-SHA256(CK || IK, S)`, where the key `K = CK || IK` (32 bytes)
/// and the message S is constructed as:
///
/// ```
/// S = 0x20 || ANID(UTF-8) || uint16_be(len(ANID)) || SQN⊕AK(6 bytes) || 0x00 0x06
/// ```
///
/// The 32-byte HMAC output is split: CK' = first 16 bytes, IK' = last 16 bytes.
///
/// The buffer layout mirrors [#generateKasme] (FC = 0x10
/// for EPS-AKA KASME), which uses the same TS 33.220 KDF family with P1 = SQN⊕AK
/// and L1 = 0x0006.
public final class EapAkaPrimeKeyDerivation {

    private static final byte FC = 0x20;
    private static final int CK_IK_LENGTH = 16;
    private static final int SQN_XOR_AK_LENGTH = 6;

    public CkIkPrime derive(final byte[] ck, final byte[] ik, final String anid, final byte[] sqnXorAk) {
        requireLength(ck, CK_IK_LENGTH, "CK");
        requireLength(ik, CK_IK_LENGTH, "IK");
        requireLength(sqnXorAk, SQN_XOR_AK_LENGTH, "SQN⊕AK");
        if (anid == null || anid.isEmpty()) {
            throw new IllegalArgumentException("ANID must not be null or empty");
        }

        final var anidBytes = anid.getBytes(StandardCharsets.UTF_8);

        final var s = new byte[1 + anidBytes.length + 2 + SQN_XOR_AK_LENGTH + 2];
        s[0] = FC; // FC = 0x20 (TS 33.402 Annex A / RFC 9048 §3.3)
        System.arraycopy(anidBytes, 0, s, 1, anidBytes.length); // P0 = ANID
        final var l0Offset = 1 + anidBytes.length;
        s[l0Offset] = (byte) (anidBytes.length >>> 8); // L0 = length of P0, big-endian 2 bytes (TS 33.220 §B.2)
        s[l0Offset + 1] = (byte) anidBytes.length;
        System.arraycopy(sqnXorAk, 0, s, l0Offset + 2, SQN_XOR_AK_LENGTH); // P1 = SQN⊕AK (6 bytes)
        final var l1Offset = l0Offset + 2 + SQN_XOR_AK_LENGTH;
        s[l1Offset] = 0x00;
        s[l1Offset + 1] = 0x06; // L1 = length of P1 = 6 (TS 33.220 §B.2 KDF)

        // KEY = CK ‖ IK (32 bytes); S is the message. HMAC-SHA256(KEY, S) (TS 33.402 Annex A).
        final var key = new byte[32];
        System.arraycopy(ck, 0, key, 0, CK_IK_LENGTH);
        System.arraycopy(ik, 0, key, CK_IK_LENGTH, CK_IK_LENGTH);

        final var mac = hmacSha256(key, s);
        // CK' = KDF[0..127], IK' = KDF[128..255] (TS 33.402 Annex A; 32-byte HMAC-SHA256 output).
        final var ckPrime = Arrays.copyOfRange(mac, 0, CK_IK_LENGTH);
        final var ikPrime = Arrays.copyOfRange(mac, CK_IK_LENGTH, 32);
        // ponytail: zero the intermediate HMAC key + full digest before returning. Java GC caveat,
        // but avoid leaving a 32-byte derived-secret buffer live longer than necessary.
        Arrays.fill(key, (byte) 0);
        Arrays.fill(mac, (byte) 0);
        return new CkIkPrime(ckPrime, ikPrime);
    }

    private static byte[] hmacSha256(final byte[] key, final byte[] message) {
        try {
            final var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message);
        } catch (final NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("HMAC-SHA256 not available", ex);
        }
    }

    private static void requireLength(final byte[] input, final int expected, final String name) {
        if (input == null || input.length != expected) {
            throw new IllegalArgumentException(name + " must be " + expected + " bytes");
        }
    }
}
