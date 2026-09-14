package com.sipgate.sparta.hss.diameter.s6a.common;

import com.sipgate.sparta.diameter._3gpp.common._3gppConstants;
import com.sipgate.sparta.diameter._3gpp.gx.GxConstants;
import com.sipgate.sparta.diameter._3gpp.rx.RxConstants;
import com.sipgate.sparta.diameter._3gpp.s6a.S6aConstants;
import com.sipgate.sparta.diameter.base.core.DiameterConstants;
import com.sipgate.sparta.diameter.base.core.avp.AVP;
import com.sipgate.sparta.diameter.base.core.avp.AVPKey;
import com.sipgate.sparta.diameter.ietf.mip6.integrated.Mip6IntegratedConstants;
import com.sipgate.sparta.diameter.ietf.mip6.split.Mip6SplitConstants;
import jakarta.xml.bind.DatatypeConverter;
import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/// Encodes the HSS subscription-data XML profile (the same `<SubscriptionData>` files the
/// TCP-XML interface used) into the children of the S6a Subscription-Data grouped AVP
/// (3GPP TS 29.272 §7.3.2).
///
/// The previous interface shipped the profile XML to the DRA, which performed the AVP encoding.
/// On a direct Diameter connection that encoding happens here: the profile is walked element-by-
/// element and each known element is mapped to its AVP (scalar or grouped) via [#DESCRIPTORS].
/// Elements without a descriptor are rejected, so a profile change cannot silently drop
/// subscription data on the wire. The MSISDN is always taken from the subscriber database
/// rather than the profile placeholder.
public final class SubscriptionDataEncoder {

  private enum Kind { STRING, STRING_BASE, UINT, ENUM, OCTET_HEX, GROUPED, ADDRESS }

  private record Descriptor(int code, long vendor, Kind kind, Map<String, Integer> enumValues) {}

  private static final Map<String, Integer> YES_NO_ENABLED = Map.of("yes", 0, "no", 1);

  private static final Map<String, Descriptor> DESCRIPTORS = Map.ofEntries(
      Map.entry("SubscriberStatus", new Descriptor(S6aConstants.AVP_SUBSCRIBER_STATUS, _3gppConstants.VENDOR_ID_3GPP, Kind.ENUM, Map.of("serviceGranted", 0, "operatorDeterminedBarring", 1))),
      Map.entry("NetworkAccessMode", new Descriptor(S6aConstants.AVP_NETWORK_ACCESS_MODE, _3gppConstants.VENDOR_ID_3GPP, Kind.ENUM, Map.of("packet-and-circuit", 0, "only-circuit", 1, "only-packet", 2))),
      Map.entry("AMBR", new Descriptor(S6aConstants.AVP_AMBR, _3gppConstants.VENDOR_ID_3GPP, Kind.GROUPED, Map.of())),
      Map.entry("MaxRequestedBandwithUL", new Descriptor(RxConstants.AVP_MAX_REQUESTED_BANDWIDTH_UL, _3gppConstants.VENDOR_ID_3GPP, Kind.UINT, Map.of())),
      Map.entry("MaxRequestedBandwithDL", new Descriptor(RxConstants.AVP_MAX_REQUESTED_BANDWIDTH_DL, _3gppConstants.VENDOR_ID_3GPP, Kind.UINT, Map.of())),
      Map.entry("APNOIReplacement", new Descriptor(S6aConstants.AVP_APN_OI_REPLACEMENT, _3gppConstants.VENDOR_ID_3GPP, Kind.STRING, Map.of())),
      Map.entry("APNConfigurationProfile", new Descriptor(S6aConstants.AVP_APN_CONFIGURATION_PROFILE, _3gppConstants.VENDOR_ID_3GPP, Kind.GROUPED, Map.of())),
      Map.entry("ContextIdentifier", new Descriptor(S6aConstants.AVP_CONTEXT_IDENTIFIER, _3gppConstants.VENDOR_ID_3GPP, Kind.UINT, Map.of())),
      Map.entry("AllAPNConfigIncluded", new Descriptor(S6aConstants.AVP_ALL_APN_CONFIGURATIONS_INCLUDED_INDICATOR, _3gppConstants.VENDOR_ID_3GPP, Kind.ENUM, Map.of("yes", 0, "modified", 1))),
      Map.entry("APNConfiguration", new Descriptor(S6aConstants.AVP_APN_CONFIGURATION, _3gppConstants.VENDOR_ID_3GPP, Kind.GROUPED, Map.of())),
      Map.entry("PDNType", new Descriptor(S6aConstants.AVP_PDN_TYPE, _3gppConstants.VENDOR_ID_3GPP, Kind.ENUM, Map.of("ipv4", 0, "ipv6", 1, "ipv4v6", 2, "ipv4-or-ipv6", 3))),
      Map.entry("ServiceSelection", new Descriptor(Mip6IntegratedConstants.AVP_SERVICE_SELECTION, 0L, Kind.STRING_BASE, Map.of())),
      Map.entry("EpsSubscribedQosProfile", new Descriptor(S6aConstants.AVP_EPS_SUBSCRIBED_QOS_PROFILE, _3gppConstants.VENDOR_ID_3GPP, Kind.GROUPED, Map.of())),
      Map.entry("QosClassIdentifier", new Descriptor(GxConstants.AVP_QOS_CLASS_IDENTIFIER, _3gppConstants.VENDOR_ID_3GPP, Kind.ENUM, Map.of())),
      Map.entry("AllocationRetentionPriority", new Descriptor(GxConstants.AVP_ALLOCATION_RETENTION_PRIORITY, _3gppConstants.VENDOR_ID_3GPP, Kind.GROUPED, Map.of())),
      Map.entry("PriorityLevel", new Descriptor(GxConstants.AVP_PRIORITY_LEVEL, _3gppConstants.VENDOR_ID_3GPP, Kind.UINT, Map.of())),
      Map.entry("PreemptionCapability", new Descriptor(GxConstants.AVP_PRE_EMPTION_CAPABILITY, _3gppConstants.VENDOR_ID_3GPP, Kind.ENUM, YES_NO_ENABLED)),
      Map.entry("PreemptionVulnerability", new Descriptor(GxConstants.AVP_PRE_EMPTION_VULNERABILITY, _3gppConstants.VENDOR_ID_3GPP, Kind.ENUM, YES_NO_ENABLED)),
      Map.entry("VPlmnDynamicAddrAllowed", new Descriptor(S6aConstants.AVP_VPLMN_DYNAMIC_ADDRESS_ALLOWED, _3gppConstants.VENDOR_ID_3GPP, Kind.ENUM, Map.of("no", 0, "yes", 1))),
      Map.entry("SIPTOPermission", new Descriptor(S6aConstants.AVP_SIPTO_PERMISSION, _3gppConstants.VENDOR_ID_3GPP, Kind.ENUM, Map.of("yes", 0, "no", 1))),
      Map.entry("LIPAPermission", new Descriptor(S6aConstants.AVP_LIPA_PERMISSION, _3gppConstants.VENDOR_ID_3GPP, Kind.ENUM, Map.of("prohibited", 0, "only", 1, "conditional", 2))),
      Map.entry("PDNGWAllocationType", new Descriptor(S6aConstants.AVP_PDN_GW_ALLOCATION_TYPE, _3gppConstants.VENDOR_ID_3GPP, Kind.ENUM, Map.of("static", 0, "dynamic", 1))),
      Map.entry("ChargingCharacteristics", new Descriptor(S6aConstants.AVP_CHARGING_CHARACTERISTICS_3GPP, _3gppConstants.VENDOR_ID_3GPP, Kind.STRING, Map.of())),
      Map.entry("RATFreqSelPriorityID", new Descriptor(S6aConstants.AVP_RAT_FREQUENCY_SELECTION_PRIORITY_ID, _3gppConstants.VENDOR_ID_3GPP, Kind.UINT, Map.of())),
      // P-GW pinning per APN — TS 29.272 §7.3.35 allows MIP6-Agent-Info inside APN-Configuration
      Map.entry("MIP6AgentInfo", new Descriptor(Mip6SplitConstants.AVP_MIP6_AGENT_INFO, 0L, Kind.GROUPED, Map.of())),
      Map.entry("MIPHomeAgentHost", new Descriptor(Mip6SplitConstants.AVP_MIP_HOME_AGENT_HOST, 0L, Kind.GROUPED, Map.of())),
      Map.entry("MIPHomeAgentAddress", new Descriptor(Mip6SplitConstants.AVP_MIP_HOME_AGENT_ADDRESS, 0L, Kind.ADDRESS, Map.of())),
      Map.entry("DestinationRealm", new Descriptor(DiameterConstants.AVP_DESTINATION_REALM, 0L, Kind.STRING_BASE, Map.of())),
      Map.entry("DestinationHost", new Descriptor(DiameterConstants.AVP_DESTINATION_HOST, 0L, Kind.STRING_BASE, Map.of()))
  );

  private final DocumentBuilderFactory documentBuilderFactory;

  public SubscriptionDataEncoder() {
    this.documentBuilderFactory = DocumentBuilderFactory.newInstance();
  }

  /// Encodes a `<SubscriptionData>` profile document into the AVPs that make up the
  /// Subscription-Data grouped AVP. The `MSISDN` element of the profile is ignored.
  public List<AVP> encode(final byte[] profileXml) {
    final Document document = parse(profileXml);
    final var root = document.getDocumentElement();
    final List<AVP> avps = new ArrayList<>();
    encodeChildren(root, avps);
    return avps;
  }

  private void encodeChildren(final Element parent, final List<AVP> target) {
    final var children = parent.getChildNodes();
    for (var i = 0; i < children.getLength(); i++) {
      final var node = children.item(i);
      if (node.getNodeType() != Node.ELEMENT_NODE) {
        continue;
      }
      final var element = (Element) node;
      final var name = element.getTagName();
      if ("MSISDN".equals(name)) {
        continue; // always sourced from the subscriber database, never the profile
      }
      final var descriptor = DESCRIPTORS.get(name);
      if (descriptor == null) {
        // failing loudly beats silently dropping subscription data on the wire
        throw new IllegalArgumentException("Unknown element <" + name + "> in subscription-data profile");
      }
      final var avp = encode(element, descriptor);
      if (avp != null) {
        target.add(avp);
      }
    }
  }

  private AVP encode(final Element element, final Descriptor descriptor) {
    final var key = new AVPKey(descriptor.code(), descriptor.vendor());
    return switch (descriptor.kind()) {
      case GROUPED -> {
        final List<AVP> nested = new ArrayList<>();
        encodeChildren(element, nested);
        yield AVP.create(key, nested);
      }
      case STRING, STRING_BASE -> AVP.create(key, text(element));
      case UINT -> AVP.create(key, Long.parseLong(text(element)));
      case ENUM -> AVP.create(key, enumValue(descriptor, text(element)));
      case OCTET_HEX -> AVP.create(key, DatatypeConverter.parseHexBinary(text(element)));
      case ADDRESS -> AVP.create(key, InetAddress.ofLiteral(text(element)));
    };
  }

  private static int enumValue(final Descriptor descriptor, final String text) {
    final var mapped = descriptor.enumValues().get(text);
    if (mapped != null) {
      return mapped;
    }
    return Integer.parseInt(text); // numeric enumerated values (e.g. QoS-Class-Identifier)
  }

  private static String text(final Element element) {
    return element.getTextContent().trim();
  }

  private Document parse(final byte[] xml) {
    try {
      final DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
      return builder.parse(new ByteArrayInputStream(xml));
    } catch (final Exception e) {
      throw new IllegalStateException("Unable to parse subscription-data profile", e);
    }
  }
}
