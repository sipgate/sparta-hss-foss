package com.sipgate.sparta.hss.diameter.common.auth;

import static jakarta.xml.bind.DatatypeConverter.parseHexBinary;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EapAkaPrimeKeyDerivationTest {

    // RFC 9048 Appendix D, Case 1 (MILENAGE test set 19, TS 35.208).
    // Network name "WLAN"; AUTN = bb52e91c747ac3ab2a5c23d15ee351d5
    // → SQN⊕AK = AUTN[0..6] = bb52e91c747a.
    private static final byte[] CK_1 = parseHexBinary("5349fbe098649f948f5d2e973a81c00f");
    private static final byte[] IK_1 = parseHexBinary("9744871ad32bf9bbd1dd5ce54e3e2e5a");
    private static final byte[] SQN_XOR_AK_1 = parseHexBinary("bb52e91c747a");
    private static final byte[] CK_PRIME_1 = parseHexBinary("0093962d0dd84aa5684b045c9edffa04");
    private static final byte[] IK_PRIME_1 = parseHexBinary("ccfc230ca74fcc96c0a5d61164f5a76c");

    // RFC 9048 Appendix D, Case 2 (same CK/IK/AUTN as Case 1, network name "HRPD").
    private static final byte[] CK_PRIME_2 = parseHexBinary("3820f0277fa5f77732b1fb1d90c1a0da");
    private static final byte[] IK_PRIME_2 = parseHexBinary("db94a0ab557ef6c9ab48619ca05b9a9f");

    // RFC 9048 Appendix D, Case 3 (artificial AKA outputs, network name "WLAN").
    private static final byte[] CK_3 = parseHexBinary("c0c0c0c0c0c0c0c0c0c0c0c0c0c0c0c0");
    private static final byte[] IK_3 = parseHexBinary("b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0");
    private static final byte[] SQN_XOR_AK_3 = parseHexBinary("a0a0a0a0a0a0");
    private static final byte[] CK_PRIME_3 = parseHexBinary("cd4c8e5c68f57dd1d7d7dfd0c538e577");
    private static final byte[] IK_PRIME_3 = parseHexBinary("3ece6b705dbbf7dfc459a11280c65524");

    // RFC 9048 Appendix D, Case 4 (same artificial CK/IK/AUTN as Case 3, network name "HRPD").
    // The full vector also carries Identity "0555444333222111", RAND/RES and the EAP session
    // keys K_encr/K_aut/K_re/MSK/EMSK; the latter come from MK = PRF'(IK'||CK', "EAP-AKA'"|Identity)
    // (RFC 9048 §3.4), which is beyond this class's CK'/IK'-only scope, so only CK'/IK'
    // are asserted here.
    private static final byte[] CK_PRIME_4 = parseHexBinary("8310a71ce6f754889613da8f64d5fb46");
    private static final byte[] IK_PRIME_4 = parseHexBinary("5adf14360ae838192db23f6fcb7f8c76");

    private final EapAkaPrimeKeyDerivation underTest = new EapAkaPrimeKeyDerivation();

    @Test
    void itDerivesCkIkPrimeForWlanNetwork() {
        // GIVEN — RFC 9048 §D Case 1: CK, IK, ANID "WLAN", SQN⊕AK from AUTN
        // WHEN
        final var result = underTest.derive(CK_1, IK_1, "WLAN", SQN_XOR_AK_1);
        // THEN
        assertThat(result.ckPrime()).containsExactly(CK_PRIME_1);
        assertThat(result.ikPrime()).containsExactly(IK_PRIME_1);
    }

    @Test
    void itDerivesDifferentKeysForDifferentNetworkName() {
        // GIVEN — RFC 9048 §D Case 2: same inputs as Case 1 but ANID "HRPD"
        // WHEN
        final var result = underTest.derive(CK_1, IK_1, "HRPD", SQN_XOR_AK_1);
        // THEN
        assertThat(result.ckPrime()).containsExactly(CK_PRIME_2);
        assertThat(result.ikPrime()).containsExactly(IK_PRIME_2);
    }

    @Test
    void itDerivesCkIkPrimeForArtificialAkaOutputs() {
        // GIVEN — RFC 9048 §D Case 3: artificial CK/IK/AUTN, ANID "WLAN"
        // WHEN
        final var result = underTest.derive(CK_3, IK_3, "WLAN", SQN_XOR_AK_3);
        // THEN
        assertThat(result.ckPrime()).containsExactly(CK_PRIME_3);
        assertThat(result.ikPrime()).containsExactly(IK_PRIME_3);
    }

    @Test
    void itDerivesCkIkPrimeForArtificialAkaOutputsHrpd() {
        // GIVEN — RFC 9048 §D Case 4: artificial CK/IK/AUTN (same as Case 3), ANID "HRPD"
        // WHEN
        final var result = underTest.derive(CK_3, IK_3, "HRPD", SQN_XOR_AK_3);
        // THEN
        assertThat(result.ckPrime()).containsExactly(CK_PRIME_4);
        assertThat(result.ikPrime()).containsExactly(IK_PRIME_4);
    }

    @Test
    void itRejectsInvalidInputLengths() {
        // GIVEN
        final var shortKey = new byte[15];
        final var shortSqnXorAk = new byte[5];
        // WHEN / THEN
        assertThatThrownBy(() -> underTest.derive(shortKey, IK_1, "WLAN", SQN_XOR_AK_1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> underTest.derive(CK_1, shortKey, "WLAN", SQN_XOR_AK_1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> underTest.derive(CK_1, IK_1, "", SQN_XOR_AK_1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> underTest.derive(CK_1, IK_1, "WLAN", shortSqnXorAk))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
