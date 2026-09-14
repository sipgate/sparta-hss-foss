package com.sipgate.sparta.hss.diameter.s6a.common;

import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.VENDOR_ID_3GPP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.sipgate.sparta.diameter._3gpp.common._3gppConstants;
import com.sipgate.sparta.diameter.base.core.avp.AVP;
import com.sipgate.sparta.diameter._3gpp.s6a.S6aConstants;
import com.sipgate.sparta.diameter.base.core.avp.AVPKey;
import com.sipgate.sparta.diameter.base.core.avp.GroupedAVP;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SubscriptionDataFactoryTest {

  private SubscriptionDataFactory underTest;

  private static SubscriptionDataFactory factory(final String profileDir) {
    return new SubscriptionDataFactory(profileDir, new SimpleMeterRegistry(), new SubscriptionDataEncoder());
  }

  private static String stringAvp(final List<AVP> avps, final int code) {
    return avps.stream()
        .filter(avp -> avp.isSameKey(new AVPKey(code, VENDOR_ID_3GPP)))
        .map(AVP::getDataAsString)
        .findFirst()
        .orElse(null);
  }

  @BeforeEach
  void setUp() {
    underTest = factory("src/test/resources/imsiProfiles");
    underTest.init();
  }

  @Test
  void whenPathDoesNotExistThenThrowIllegalStateException() {
    final var underTest = factory("src/does-not-exist");
    assertThat(catchThrowable(underTest::init)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void whenNoDefaultXmlExistsThenIllegalStateException() {
    final var underTest = factory("src/test/resources/noDefaultXml");
    assertThat(catchThrowable(underTest::init)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void whenBrokenXmlThenIllegalStateException() {
    final var underTest = factory("src/test/resources/invalidXml");
    assertThat(catchThrowable(underTest::init)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void whenProfileExistsThenCreateSubscriptionData() {
    final var actual = underTest.createSubscriptionData("vplmnId", "no-volte", "9999990214857");
    assertThat(actual).isNotEmpty();
  }

  @Test
  void whenProfileHasMultipleApnsThenSubscriptionDataHasMultipleAvps() {
      final List<AVP> actual = underTest.createSubscriptionData("vplmnId", "default", "my-msisdn");

      final GroupedAVP profile = (GroupedAVP) actual.stream()
          .filter(avp -> avp.isSameKey(new AVPKey(S6aConstants.AVP_APN_CONFIGURATION_PROFILE, VENDOR_ID_3GPP)))
          .findFirst()
          .orElseThrow();

      final List<AVP> configurations = profile.findAVPs(new AVPKey(S6aConstants.AVP_APN_CONFIGURATION, VENDOR_ID_3GPP));
      assertThat(configurations).hasSize(2);
  }

  @Test
  void whenProfileHasMsisdnAndFunctionIsCalledWithMsIsdnThenSubscriptionDataHasOneMsisdn() {
      final var actual = underTest.createSubscriptionData("vplmnId", "default", "9999990214857");

      final var numMsisdns = actual.stream()
          .filter(avp -> avp.isSameKey(new AVPKey(_3gppConstants.AVP_MSISDN, VENDOR_ID_3GPP)))
          .count();

      assertThat(numMsisdns).isOne();
  }

  @Test
  void whenProfileNotExistsThenThrowException() {
    final var thrown = catchThrowable(() -> underTest.createSubscriptionData("vplmnId", "does-not-exist", "9999990214857"));
    assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @CsvSource({
      "20620, default-profile-belgium-base", // GIVEN: only one profile for the MCC-MNC combination
      "23003, default-profile-czech-republic", // GIVEN: only one profile for the MCC
      "21401, default-profile-spain-vodafone", // GIVEN: one profile for the MCC and a specific one for the MNC
      "21403, default-profile-spain", // GIVEN: one profile for the MCC and a specific one for another MNC
  })
  void whenVplmnIdSpecifiesProfileThenUseIt(final String vplmnId, final String apnOiReplacement) {
    // WHEN
    final var actual = underTest.createSubscriptionData(vplmnId, "default", "9999990214857");

    // THEN
    assertThat(stringAvp(actual, S6aConstants.AVP_APN_OI_REPLACEMENT)).isEqualTo(apnOiReplacement);
  }
}
