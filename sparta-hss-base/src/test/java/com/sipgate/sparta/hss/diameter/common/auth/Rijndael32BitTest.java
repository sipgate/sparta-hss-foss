package com.sipgate.sparta.hss.diameter.common.auth;

import static jakarta.xml.bind.DatatypeConverter.parseHexBinary;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * - A "black box" test data document [5]
 * 3GPP TS 35.208: "3rd Generation Partnership Project; Technical Specification Group Services
 * and System Aspects; 3G Security; Specification of the MILENAGE Algorithm Set: An example
 * algorithm set for the 3GPP authentication and key generation functions f1, f1*, f2, f3, f4, f5 and
 * f5*; Document 4: Design Conformance Test Data".
 * <p>
 * https://www.etsi.org/deliver/etsi_ts/135200_135299/135208/17.00.00_60/ts_135208v170000p.pdf
 */
class Rijndael32BitTest {
    static Stream<Arguments> provideTestSet() {
        final var plaintexts = new String[]{
                "ee36f7cf037d37d3692f7f0399e7949a",
                "93cc3640c5d6a521d81235bd0882bf0a",
                "8f7a8f0d108b7f2d97a53eacc1d958d9",
                "68c98bbfab628ec1adf2a3d90c34a751",
                "a840b1dd60249aa322016b4b31daf3b8",
                "d66789eff5996b9cffd89e0a77148657",
                "8cbdb88c620ffe88f8ce0042a5052568",
                "83a4dbccf3ff12e2154dbf45512b5a32",
                "d117610c04f5d3d61d8cb6bc910a918b",
                "9d7cf334be57ea4d37f28c8f8f0a7259",
                "0f3c382c488efe2d8ce7eaa77093a486",
                "51c0d9dccb03b86043d378ea5f0dd0f8",
                "886f7d2c6f1dc0cc851fdd4182e09d99",
                "dfb0cd055b43bd5b7a31c2940174b44f",
                "7ced704db8df58f44972cecc0531bc04",
                "878d0feead58387e1b4aaacfb6805e9f",
                "29f852ce631605e31722fd873bc74208",
                "8995e0d70281690a666070dbbd6f0f27",
                "d1ce62efb1010adb2f6eabb263e16ee4",
                "260a7f0098fe903e9bb255f656840627"
        };

        final var ciphertexts = new String[]{
                "9e2980c59739da67b136355e3cede6a2",
                "009a9e0996561525f611667bbf79e226",
                "5d9bce854decaf0da93d28b7e35f608c",
                "db2944cce8e683cd03fff19931a12135",
                "02bffada7137c492c00e8452d8c76eaa",
                "bdf226fecf9ff9961e5c2621b764efb1",
                "fe8d6888b5a5c146efea6660f7b4e699",
                "1c3aff64cd717a6e959c95c8b9cd7d3f",
                "21bc0073cc9aa7a38125777443f8663f",
                "1591d87d8fa69176eae9fae902cd61a4",
                "c59e066976d92f8e567b806f94fb09d2",
                "ff00e5a1a02f75943b4a5a1e39bf5dc3",
                "3f65bc17b47b645b0fbfbfe8da4269bf",
                "32976e2ed7fb97b2bcaaba0080faf1e5",
                "537724474cf17c9b105673b5f37581c8",
                "e555cfb79663f0eafdcc966555d6498d",
                "d78106ad6fdae41c95dd2f7b5b479a79",
                "70b059adf647d183a18512a372ae683c",
                "ec7fc8b110246889cff7293fe30c8465",
                "91747de67effaff8578381ef27e497ec"
        };


        final var keys = new String[]{
                "465b5ce8b199b49faa5f0a2ee238a6bc",
                "0396eb317b6d1c36f19c1c84cd6ffd16",
                "fec86ba6eb707ed08905757b1bb44b8f",
                "9e5944aea94b81165c82fbf9f32db751",
                "4ab1deb05ca6ceb051fc98e77d026a84",
                "6c38a116ac280c454f59332ee35c8c4f",
                "523ace48994925ca0495efd50c7c71e2",
                "e4aee4faeeb2f93d43604f5f2e65ff25",
                "b2c211ec004d0323022e49ccd363c8ff",
                "443c5a6759e04dc2c9c6e465823dd5b6",
                "23e5c9b18034df6421f22972c3bd2d93",
                "d6856512fd11bf4f91781e3b1ea9d8de",
                "e5e436d4593ef7cba221cd071ccadfdf",
                "d9c9453e1b858f22dab53e2044e849de",
                "38fe9997cbac8d9ac4fc78cc1fcb1e22",
                "8d493a28f17cfb7de5be8354333a66f4",
                "5d73313916bde011c7c267000bfe2a9c",
                "32448511c137459ff1d12c172cdf7262",
                "93833efa7bb1316eebb3dbe320a5448d",
                "09fa653acbf5acc83e307caa6e18aa67"
        };

        final List<Arguments> arguments = new ArrayList<>();

        for (var i = 0; i < plaintexts.length; i++) {
            arguments.add(Arguments.of(
                    parseHexBinary(plaintexts[i]),
                    parseHexBinary(ciphertexts[i]),
                    parseHexBinary(keys[i]))
            );
        }

        return arguments.stream();
    }

    @ParameterizedTest
    @MethodSource("provideTestSet")
    void itEncryptsPlaintexts(final byte[] plaintext, final byte[] ciphertext, final byte[] key) {
        // GIVEN
        final var rijndael = new Rijndael32Bit();
        rijndael.init(key);

        // WHEN
        final var actual = rijndael.encrypt(plaintext);

        // THEN
        assertThat(actual).isEqualTo(ciphertext);
    }
}