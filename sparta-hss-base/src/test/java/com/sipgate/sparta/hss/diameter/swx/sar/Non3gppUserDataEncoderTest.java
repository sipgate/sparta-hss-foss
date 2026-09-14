package com.sipgate.sparta.hss.diameter.swx.sar;

import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.AVP_AMBR;
import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.AVP_APN_CONFIGURATION;
import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.AVP_APN_OI_REPLACEMENT;
import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.AVP_CONTEXT_IDENTIFIER;
import static com.sipgate.sparta.diameter._3gpp.s6a.S6aConstants.AVP_CHARGING_CHARACTERISTICS_3GPP;
import static com.sipgate.sparta.diameter._3gpp.swx.SwxConstants.AVP_NON_3GPP_IP_ACCESS;
import static com.sipgate.sparta.diameter._3gpp.swx.SwxConstants.AVP_NON_3GPP_IP_ACCESS_APN;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.sipgate.sparta.diameter._3gpp.s6a.S6aConstants;
import com.sipgate.sparta.diameter.base.core.avp.AVP;
import com.sipgate.sparta.hss.diameter.s6a.common.SubscriptionDataEncoder;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class Non3gppUserDataEncoderTest {

    // Mirrors imsiProfiles/default.xml (top-level AMBR + ChargingCharacteristics, an
    // APNConfigurationProfile wrapping Context-Identifier + AllAPNConfigIncluded + TWO APNConfigurations),
    // plus a Subscription-Data-level APNOIReplacement (S6a §7.3.2 allows it there) to cover the top-level lift.
    private static final String PROFILE = """
        <SubscriptionData>
          <SubscriberStatus>serviceGranted</SubscriberStatus>
          <NetworkAccessMode>only-packet</NetworkAccessMode>
          <AMBR><MaxRequestedBandwithUL>32000000</MaxRequestedBandwithUL>
                <MaxRequestedBandwithDL>50000000</MaxRequestedBandwithDL></AMBR>
          <ChargingCharacteristics>0800</ChargingCharacteristics>
          <APNOIReplacement>example.mnc999.mcc999.gprs</APNOIReplacement>
          <APNConfigurationProfile>
            <ContextIdentifier>1</ContextIdentifier>
            <AllAPNConfigIncluded>yes</AllAPNConfigIncluded>
            <APNConfiguration>
              <ContextIdentifier>1</ContextIdentifier><PDNType>ipv4v6</PDNType>
              <ServiceSelection>internet</ServiceSelection>
            </APNConfiguration>
            <APNConfiguration>
              <ContextIdentifier>2</ContextIdentifier><PDNType>ipv4v6</PDNType>
              <ServiceSelection>ims</ServiceSelection>
            </APNConfiguration>
          </APNConfigurationProfile>
        </SubscriptionData>""";

    private final Non3gppUserDataEncoder underTest = new Non3gppUserDataEncoder(new SubscriptionDataEncoder());

    @Test
    void itPrependsTheTwoSwxEnumsThenLiftsAmbrChargingAndApnConfigs() {
        // WHEN
        final List<Long> codes = codesOf(underTest.encode(PROFILE.getBytes(UTF_8)));

        // THEN — the two SWx-native enums lead, then the lifted profile members
        assertThat(codes).startsWith((long) AVP_NON_3GPP_IP_ACCESS, (long) AVP_NON_3GPP_IP_ACCESS_APN);
        assertThat(codes).contains((long) AVP_AMBR, (long) AVP_CHARGING_CHARACTERISTICS_3GPP, (long) AVP_CONTEXT_IDENTIFIER);
        // both APN-Configurations are lifted out of the APN-Configuration-Profile
        assertThat(codes.stream().filter(c -> c == (long) AVP_APN_CONFIGURATION).count()).isEqualTo(2L);
    }

    @Test
    void itDropsSubscriberStatusNetworkAccessModeAndTheApnConfigurationProfileWrapper() {
        // WHEN
        final List<Long> codes = codesOf(underTest.encode(PROFILE.getBytes(UTF_8)));

        // THEN — S6a-only wrapper/status AVPs are NOT present at the Non-3GPP-User-Data level
        assertThat(codes).doesNotContain(
            (long) S6aConstants.AVP_SUBSCRIBER_STATUS,
            (long) S6aConstants.AVP_APN_CONFIGURATION_PROFILE,
            (long) S6aConstants.AVP_ALL_APN_CONFIGURATIONS_INCLUDED_INDICATOR);
    }

    @Test
    void itSetsNon3gppIpAccessAllowedAndApnsEnabled() {
        // WHEN
        final List<AVP> avps = underTest.encode(PROFILE.getBytes(UTF_8));
        // THEN — Non-3GPP-IP-Access=ALLOWED(0), Non-3GPP-IP-Access-APN=ENABLE(0)
        assertThat(avps.get(0).getDataAsInt()).isZero();
        assertThat(avps.get(1).getDataAsInt()).isZero();
    }

    @Test
    void itLiftsTopLevelApnOiReplacement() {
        // GIVEN the PROFILE carries a Subscription-Data-level APNOIReplacement (distinct from the
        // per-APNConfiguration one in default.xml) — WHEN encoded
        final List<Long> codes = codesOf(underTest.encode(PROFILE.getBytes(UTF_8)));

        // THEN — APN-OI-Replacement is kept at the Non-3GPP-User-Data level (TS 29.273 §8.2.3.1)
        assertThat(codes).contains((long) AVP_APN_OI_REPLACEMENT);
    }

    private static List<Long> codesOf(final List<AVP> avps) {
        final var codes = new ArrayList<Long>();
        for (final var avp : avps) {
            codes.add(avp.getCode());   // AVP.getCode() returns long
        }
        return codes;
    }
}
