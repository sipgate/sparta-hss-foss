package com.sipgate.sparta.hss.diameter.swx.sar;

import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.AVP_AMBR;
import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.AVP_APN_CONFIGURATION;
import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.AVP_APN_CONFIGURATION_PROFILE;
import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.AVP_APN_OI_REPLACEMENT;
import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.AVP_CONTEXT_IDENTIFIER;
import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.VENDOR_ID_3GPP;
import static com.sipgate.sparta.diameter._3gpp.s6a.S6aConstants.AVP_CHARGING_CHARACTERISTICS_3GPP;
import static com.sipgate.sparta.diameter._3gpp.swx.SwxConstants.AVP_NON_3GPP_IP_ACCESS;
import static com.sipgate.sparta.diameter._3gpp.swx.SwxConstants.AVP_NON_3GPP_IP_ACCESS_APN;

import com.sipgate.sparta.diameter.base.core.avp.AVP;
import com.sipgate.sparta.diameter.base.core.avp.AVPKey;
import com.sipgate.sparta.diameter.base.core.avp.GroupedAVP;
import com.sipgate.sparta.hss.diameter.s6a.common.SubscriptionDataEncoder;
import java.util.ArrayList;
import java.util.List;

/// Builds the children of the SWx Non-3GPP-User-Data grouped AVP (TS 29.273 §8.2.3.1) from the HSS
/// subscription-data XML profile. Reuses the S6a [SubscriptionDataEncoder] to produce the
/// Subscription-Data AVP tree, then flattens it: lift AMBR / APN-OI-Replacement /
/// 3GPP-Charging-Characteristics, unwrap APN-Configuration-Profile to lift its Context-Identifier +
/// APN-Configuration children, and prepend Non-3GPP-IP-Access (ALLOWED) + Non-3GPP-IP-Access-APN
/// (ENABLE). The S6a-only AVPs (Subscriber-Status, Network-Access-Mode,
/// All-APN-Configurations-Included-Indicator) are not in the §8.2.3.1 ABNF and are dropped.
public final class Non3gppUserDataEncoder {

    private static final int NON_3GPP_SUBSCRIPTION_ALLOWED = 0;  // TS 29.273 §8.2.3.3
    private static final int NON_3GPP_APNS_ENABLE = 0;           // TS 29.273 §8.2.3.4

    private final SubscriptionDataEncoder subscriptionDataEncoder;

    public Non3gppUserDataEncoder(final SubscriptionDataEncoder subscriptionDataEncoder) {
        this.subscriptionDataEncoder = subscriptionDataEncoder;
    }

    /// @return the Non-3GPP-User-Data child AVPs (Subscription-ID is added — if ever supported — by the factory).
    public List<AVP> encode(final byte[] profileXml) {
        final List<AVP> subscriptionData = subscriptionDataEncoder.encode(profileXml);
        final List<AVP> result = new ArrayList<>();
        result.add(AVP.create(new AVPKey(AVP_NON_3GPP_IP_ACCESS, VENDOR_ID_3GPP), NON_3GPP_SUBSCRIPTION_ALLOWED));
        result.add(AVP.create(new AVPKey(AVP_NON_3GPP_IP_ACCESS_APN, VENDOR_ID_3GPP), NON_3GPP_APNS_ENABLE));
        for (final var avp : subscriptionData) {
            final long code = avp.getCode();
            if (code == AVP_AMBR || code == AVP_APN_OI_REPLACEMENT || code == AVP_CHARGING_CHARACTERISTICS_3GPP) {
                result.add(avp);
            } else if (code == AVP_APN_CONFIGURATION_PROFILE && avp instanceof GroupedAVP profile) {
                liftApnConfigurationProfile(profile, result);
            }
            // SubscriberStatus / NetworkAccessMode / RATFreqSelPriorityID etc.: not part of §8.2.3.1
        }
        return result;
    }

    /// APN-Configuration-Profile (1429) wraps Context-Identifier + All-APN-Configurations-Included-Indicator
    /// + APN-Configuration*. Non-3GPP-User-Data wants Context-Identifier + APN-Configuration* directly,
    /// so lift those and drop the All-APN-Configurations-Included-Indicator (not in the §8.2.3.1 ABNF).
    private static void liftApnConfigurationProfile(final GroupedAVP profile, final List<AVP> target) {
        for (final var child : profile.getAVPs()) {
            final long code = child.getCode();
            if (code == AVP_CONTEXT_IDENTIFIER || code == AVP_APN_CONFIGURATION) {
                target.add(child);
            }
        }
    }
}
