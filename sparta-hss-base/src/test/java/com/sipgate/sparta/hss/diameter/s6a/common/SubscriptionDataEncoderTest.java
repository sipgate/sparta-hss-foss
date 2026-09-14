package com.sipgate.sparta.hss.diameter.s6a.common;

import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.VENDOR_ID_3GPP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sipgate.sparta.diameter._3gpp.common._3gppConstants;
import com.sipgate.sparta.diameter._3gpp.s6a.S6aConstants;
import com.sipgate.sparta.diameter.base.core.DiameterConstants;
import com.sipgate.sparta.diameter.base.core.avp.AVP;
import com.sipgate.sparta.diameter.base.core.avp.AVPKey;
import com.sipgate.sparta.diameter.base.core.avp.AVPParseException;
import com.sipgate.sparta.diameter.base.core.avp.GroupedAVP;
import com.sipgate.sparta.diameter.ietf.mip6.integrated.Mip6IntegratedConstants;
import com.sipgate.sparta.diameter.ietf.mip6.split.Mip6SplitConstants;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

class SubscriptionDataEncoderTest {

  private final SubscriptionDataEncoder underTest = new SubscriptionDataEncoder();

  static Stream<Path> xmlProfiles() throws Exception {
    return Files.list(Paths.get("src/test/resources/imsiProfiles")).filter(p -> p.toString().endsWith(".xml"));
  }

  /** Counts all element nodes below {@code parent}, skipping the MSISDN placeholder the encoder ignores. */
  private static int countEncodableElements(final Element parent) {
    var count = 0;
    final var children = parent.getChildNodes();
    for (var i = 0; i < children.getLength(); i++) {
      final var node = children.item(i);
      if (node.getNodeType() != Node.ELEMENT_NODE || "MSISDN".equals(((Element) node).getTagName())) {
        continue;
      }
      count += 1 + countEncodableElements((Element) node);
    }
    return count;
  }

  private static int countAvps(final List<AVP> avps) {
    var count = 0;
    for (final AVP avp : avps) {
      count += 1 + (avp instanceof GroupedAVP grouped ? countAvps(grouped.getAVPs()) : 0);
    }
    return count;
  }

  private static GroupedAVP findGrouped(final List<AVP> avps, final AVPKey key) {
    return (GroupedAVP) avps.stream().filter(avp -> avp.isSameKey(key)).findFirst().orElseThrow(
        () -> new AssertionError("expected grouped AVP " + key + " not found"));
  }

  @ParameterizedTest
  @MethodSource("xmlProfiles")
  void shouldEncodeXmlProfilesSuccessfully(final Path profileXmlPath) throws Exception {
    final byte[] profileXml = Files.readAllBytes(profileXmlPath);

    final List<AVP> actual = underTest.encode(profileXml);

    assertThat(actual).isNotEmpty();

    // Completeness: every element of the profile (except the MSISDN placeholder) must end up
    // as exactly one AVP — nothing may be dropped on the way to the wire.
    final var document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(new ByteArrayInputStream(profileXml));
    assertThat(countAvps(actual)).isEqualTo(countEncodableElements(document.getDocumentElement()));

    // Check MSISDN is NOT present here (handled by factory now)
    final boolean hasMsisdn = actual.stream()
        .anyMatch(avp -> avp.isSameKey(new AVPKey(_3gppConstants.AVP_MSISDN, VENDOR_ID_3GPP)));
    assertThat(hasMsisdn).isFalse();

    // Check APNConfigurationProfile
    final GroupedAVP apnConfigProfile = (GroupedAVP) actual.stream()
        .filter(avp -> avp.isSameKey(new AVPKey(S6aConstants.AVP_APN_CONFIGURATION_PROFILE, VENDOR_ID_3GPP)))
        .findFirst()
        .orElse(null);

    if (profileXmlPath.getFileName().toString().contains("default") || profileXmlPath.getFileName().toString().contains("volte")) {
      assertThat(apnConfigProfile).isNotNull();

      final List<AVP> profileChildren = apnConfigProfile.getAVPs();
      
      // Check ContextIdentifier inside APNConfigurationProfile
      final boolean hasContextId = profileChildren.stream()
          .anyMatch(avp -> avp.isSameKey(new AVPKey(S6aConstants.AVP_CONTEXT_IDENTIFIER, VENDOR_ID_3GPP)));
      assertThat(hasContextId).isTrue();

      // Check APNConfiguration inside APNConfigurationProfile
      final long apnConfigCount = profileChildren.stream()
          .filter(avp -> avp.isSameKey(new AVPKey(S6aConstants.AVP_APN_CONFIGURATION, VENDOR_ID_3GPP)))
          .count();
      assertThat(apnConfigCount).isGreaterThan(0);
      
      // Extract first APNConfiguration
      final GroupedAVP firstApnConfig = (GroupedAVP) profileChildren.stream()
          .filter(avp -> avp.isSameKey(new AVPKey(S6aConstants.AVP_APN_CONFIGURATION, VENDOR_ID_3GPP)))
          .findFirst()
          .orElseThrow();
          
      final List<AVP> apnConfigChildren = firstApnConfig.getAVPs();
      
      // Check ContextIdentifier inside APNConfiguration
      assertThat(apnConfigChildren.stream().anyMatch(avp -> avp.isSameKey(new AVPKey(S6aConstants.AVP_CONTEXT_IDENTIFIER, VENDOR_ID_3GPP)))).isTrue();
      
      // Check PDNType
      assertThat(apnConfigChildren.stream().anyMatch(avp -> avp.isSameKey(new AVPKey(S6aConstants.AVP_PDN_TYPE, VENDOR_ID_3GPP)))).isTrue();
      
      // Check ServiceSelection
      assertThat(apnConfigChildren.stream().anyMatch(avp -> avp.isSameKey(new AVPKey(Mip6IntegratedConstants.AVP_SERVICE_SELECTION, 0L)))).isTrue();
    }
  }

  @Test
  void shouldEncodeMultipleApnConfiguration() throws IOException, AVPParseException {
      final byte[] profileXml = Files.readAllBytes(Path.of("src/test/resources/imsiProfiles/default.xml"));
      // WHEN: gathering AVPs
      final List<AVP> actual = underTest.encode(profileXml);

      final GroupedAVP profile = (GroupedAVP) actual.stream()
          .filter(avp -> avp.isSameKey(new AVPKey(S6aConstants.AVP_APN_CONFIGURATION_PROFILE, VENDOR_ID_3GPP)))
          .findFirst()
          .orElseThrow();

      // THEN: multiple AVPs in grouped AVP
      final List<AVP> configurations = profile.findAVPs(new AVPKey(S6aConstants.AVP_APN_CONFIGURATION, VENDOR_ID_3GPP));
      assertThat(configurations).hasSize(2);

      // WHEN: actually encoding to bits
      final var baos = new ByteArrayOutputStream();
      profile.writeTo(new DataOutputStream(baos));

      // THEN: decodes with multiple configurations
      final GroupedAVP decodedProfile = (GroupedAVP) AVP.readFrom(ByteBuffer.wrap(baos.toByteArray()));
      final List<AVP> decodedConfigurations = decodedProfile.findAVPs(new AVPKey(S6aConstants.AVP_APN_CONFIGURATION, VENDOR_ID_3GPP));
      assertThat(decodedConfigurations).hasSize(2);
  }

  @Test
  void shouldEncodeMip6AgentInfoSubtree() throws Exception {
    final URL res =  ClassLoader.getSystemResource("default-mip6-agent-info-host.xml");
    final byte[] data = res.openStream().readAllBytes();

    final List<AVP> actual = underTest.encode(data);

    final var apnConfigProfile = findGrouped(actual, new AVPKey(S6aConstants.AVP_APN_CONFIGURATION_PROFILE, VENDOR_ID_3GPP));
    final var imsApnConfig = (GroupedAVP) apnConfigProfile.getAVPs().stream()
        .filter(avp -> avp.isSameKey(new AVPKey(S6aConstants.AVP_APN_CONFIGURATION, VENDOR_ID_3GPP)))
        .map(GroupedAVP.class::cast)
        .filter(apn -> apn.getAVPs().stream().anyMatch(child ->
            child.isSameKey(new AVPKey(Mip6SplitConstants.AVP_MIP6_AGENT_INFO, 0L))))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no APN-Configuration with MIP6-Agent-Info found"));

    final var mip6AgentInfo = findGrouped(imsApnConfig.getAVPs(), new AVPKey(Mip6SplitConstants.AVP_MIP6_AGENT_INFO, 0L));

    final var mipHomeAgentAddress = mip6AgentInfo.findAVP(new AVPKey(Mip6SplitConstants.AVP_MIP_HOME_AGENT_ADDRESS, 0L));
    assertThat(mipHomeAgentAddress.getDataAsIPAddress()).isEqualTo(Inet4Address.ofLiteral("128.66.13.37"));

    final var mipHomeAgentHost = findGrouped(mip6AgentInfo.getAVPs(), new AVPKey(Mip6SplitConstants.AVP_MIP_HOME_AGENT_HOST, 0L));

    final var destinationRealm = mipHomeAgentHost.findAVP(new AVPKey(DiameterConstants.AVP_DESTINATION_REALM, 0L));
    final var destinationHost = mipHomeAgentHost.findAVP(new AVPKey(DiameterConstants.AVP_DESTINATION_HOST, 0L));
    assertThat(destinationRealm.getDataAsString()).isEqualTo("epc.mnc999.mcc999.3gppnetwork.org");
    assertThat(destinationHost.getDataAsString()).isEqualTo("ims.example.mnc999.mcc999.gprs");
  }

  @Test
  void shouldThrowWhenXmlContainsUnknownElement() {
    final String unknownXml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <SubscriptionData>
            <SubscriberStatus>serviceGranted</SubscriberStatus>
            <SomeUnknownElement>whatever</SomeUnknownElement>
        </SubscriptionData>
        """;

    assertThatThrownBy(() -> underTest.encode(unknownXml.getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SomeUnknownElement");
  }

  @Test
  void shouldThrowWhenXmlContainsBadIntegerData() {
    final String badXml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <SubscriptionData>
            <MaxRequestedBandwithUL>not-a-number</MaxRequestedBandwithUL>
        </SubscriptionData>
        """;
    
    assertThatThrownBy(() -> underTest.encode(badXml.getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(NumberFormatException.class);
  }
}
