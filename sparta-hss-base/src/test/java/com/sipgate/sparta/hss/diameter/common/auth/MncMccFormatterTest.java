package com.sipgate.sparta.hss.diameter.common.auth;

import static jakarta.xml.bind.DatatypeConverter.parseHexBinary;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MncMccFormatterTest {

    @ParameterizedTest
    @CsvSource({
            "26203,262,03",
            "262003,262,003",
            "00101,001,01",
            "001001,001,001",
            "99999,999,99",
            "999999,999,999",
            "35001,350,01", // Bermuda, Digicel
            "350000,350,000", // Bermuda, One
    })
    void should_extract_correct_mcc_mnc(final String input, final String mcc, final String mnc) {
        assertThat(MncMccFormatter.plmnToMcc(input)).isEqualTo(mcc);
        assertThat(MncMccFormatter.plmnToMnc(input)).isEqualTo(mnc);
    }

    @ParameterizedTest
    @CsvSource({
            // examples from <https://nickvsnetworking.com/plmn-identifier-calculation-mcc-mnc-to-plmn/>
            "505,93,05f539",
            "310,410,130014", // USA, AT&T
            // according to ETSI TS 151 011, Section 10.3.4 EFPLMNsel (PLMN selector)
            "246,81,42f618",
            // real life examples
            "262,03,62f230", // Germany, Telefonica
            "405,872,042578", // India, Jio
            "350,01,53f010", // Bermuda, Digicel
            "350,000,530000", // Bermuda, One
    })
    void should_encode_mcc_mnc_into_serving_network_id(final String mcc, final String mnc, final String expectedSnId) {
        // GIVEN

        // WHEN
        final var actualSnId = MncMccFormatter.mccMncToSnId(mcc, mnc);

        // THEN
        assertThat(actualSnId)
            .inHexadecimal()
            .isEqualTo(parseHexBinary(expectedSnId.toUpperCase()));
    }

    @ParameterizedTest
    @CsvSource({
            "505,93,05f539",
            "310,410,130014",
            "246,81,42f618",
            "262,03,62f230",
            "405,872,042578",
            "350,01,53f010",
            "350,000,530000",
    })
    void should_decode_serving_network_id_into_mcc_mnc(final String expectedMcc, final String expectedMnc, final String snIdHex) {
        // GIVEN
        final byte[] snId = parseHexBinary(snIdHex.toUpperCase());

        // WHEN
        final var actualMccMnc = MncMccFormatter.snIdToMccMnc(snId);

        // THEN
        assertThat(actualMccMnc.mcc()).isEqualTo(expectedMcc);
        assertThat(actualMccMnc.mnc()).isEqualTo(expectedMnc);
        assertThat(actualMccMnc.plmn()).isEqualTo(expectedMcc + expectedMnc);
    }

    @ParameterizedTest
    @CsvSource({
            "epc.mnc003.mcc262.3gppnetwork.org,262,003", // Germany, Telefonica
            "epc.mnc872.mcc405.3gppnetwork.org,405,872", // India, Jio
            "epc.mnc001.mcc350.3gppnetwork.org,350,001", // Bermuda, Digicel
            // MNC may appear with 2 digits and not left-padded
            "epc.mnc03.mcc262.3gppnetwork.org,262,03",
            // case-insensitive: domain names are not case-sensitive
            "EPC.MNC003.MCC262.3GPPNETWORK.ORG,262,003",
    })
    void should_extract_mcc_mnc_from_origin_realm(final String originRealm, final String mcc, final String mnc) {
        final var actual = MncMccFormatter.originRealmToMccMnc(originRealm);

        assertThat(actual).isNotNull();
        assertThat(actual.mcc()).isEqualTo(mcc);
        assertThat(actual.mnc()).isEqualTo(mnc);
    }

    @Test
    void should_return_null_for_null_origin_realm() {
        assertThat(MncMccFormatter.originRealmToMccMnc(null)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "example.com",
            "epc.mcc262.mnc003.3gppnetwork.org", // mcc/mnc in wrong order
            "epc.mnc3.mcc262.3gppnetwork.org", // mnc not 3 digits
            "ims.mnc003.mcc262.3gppnetwork.org.evil.com", // suffix mismatch
    })
    void should_return_null_for_unparseable_origin_realm(final String originRealm) {
        assertThat(MncMccFormatter.originRealmToMccMnc(originRealm)).isNull();
    }
}