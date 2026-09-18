package com.sipgate.sparta.hss.diameter.common;

import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.AVP_IP_SM_GW_NAME;
import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.AVP_IP_SM_GW_REALM;
import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.AVP_SERVING_NODE;
import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.VENDOR_ID_3GPP;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.RES_DIAMETER_INVALID_AVP_VALUE;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.RES_DIAMETER_MISSING_AVP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchException;

import com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants;
import com.sipgate.sparta.diameter._3gpp.cxdx.messages.ServerAssignmentRequest;
import com.sipgate.sparta.diameter.base.core.Command;
import com.sipgate.sparta.diameter.base.core.ErrorAnswer;
import com.sipgate.sparta.diameter.base.core.HopByHopId;
import com.sipgate.sparta.diameter.base.core.EndToEndId;
import com.sipgate.sparta.diameter.base.core.avp.AVP;
import com.sipgate.sparta.diameter.base.core.avp.AVPKey;
import com.sipgate.sparta.diameter.base.session.DiameterErrorAnswerException;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import org.junit.jupiter.api.Test;

class ServingNodeInfoTest {

    private static final String IMSI = "999990000263716";
    private static final String IP_SM_GW_NAME = "ucn01.dev.sgwl.epc.mnc003.mcc262.3gppnetwork.org";
    private static final String IP_SM_GW_REALM = "epc.mnc003.mcc262.3gppnetwork.org";

    private static ServerAssignmentRequest.In buildSar(final List<AVP> servingNodeChildren) throws Exception {
        final var out = new ServerAssignmentRequest.Out();
        out.setUserName(IMSI + "@ims.mnc003.mcc262.3gppnetwork.org");
        out.setServerAssignmentType(CxDxConstants.SERVER_ASSIGNMENT_REGISTRATION);
        out.setOriginHost("ucn01.dev.sgwl.epc.mnc003.mcc262.3gppnetwork.org");
        out.setOriginRealm("epc.mnc003.mcc262.3gppnetwork.org");
        if (servingNodeChildren != null) {
            out.addAVP(AVP.create(
                new AVPKey(AVP_SERVING_NODE, VENDOR_ID_3GPP),
                servingNodeChildren));
        }

        final var baos = new ByteArrayOutputStream();
        out.writeTo(new DataOutputStream(baos), new HopByHopId(1), new EndToEndId(2));
        return (ServerAssignmentRequest.In) Command.parseMessage(ByteBuffer.wrap(baos.toByteArray()));
    }

    private static AVP ipSmGwName(final String value) {
        return AVP.create(new AVPKey(AVP_IP_SM_GW_NAME, VENDOR_ID_3GPP), value);
    }

    private static AVP ipSmGwRealm(final String value) {
        return AVP.create(new AVPKey(AVP_IP_SM_GW_REALM, VENDOR_ID_3GPP), value);
    }

    @Test
    void itExtractsIpSmGwNameAndRealmFromServingNode() throws Exception {
        // GIVEN an inbound SAR carrying Serving-Node{IP-SM-GW-Name, IP-SM-GW-Realm}
        final var in = buildSar(List.of(ipSmGwName(IP_SM_GW_NAME), ipSmGwRealm(IP_SM_GW_REALM)));

        // WHEN
        final var info = ServingNodeInfo.from(in);

        // THEN
        assertThat(info.hasIpSmGw()).isTrue();
        assertThat(info.ipSmGwName()).isEqualTo(IP_SM_GW_NAME);
        assertThat(info.ipSmGwRealm()).isEqualTo(IP_SM_GW_REALM);
    }

    @Test
    void itReturnsNullWhenServingNodeIsMissing() throws Exception {
        // GIVEN an inbound SAR without Serving-Node — legitimate absence, e.g. WiFi-only UCN
        final var in = buildSar(null);

        // WHEN
        final var info = ServingNodeInfo.from(in);

        // THEN
        assertThat(info).isNull();
    }

    @Test
    void itRejectsNameOnlyServingNodeWithMissingAvp() throws Exception {
        // GIVEN an inbound SAR with a Serving-Node that only carries the name
        // (both AVPs are required to identify the IP-SM-GW, and both DB columns are NOT NULL)
        final var in = buildSar(List.of(ipSmGwName(IP_SM_GW_NAME)));

        // WHEN
        final var e = catchException(() -> ServingNodeInfo.from(in));

        // THEN the missing realm is reported via DIAMETER_MISSING_AVP with Failed-AVP = realm key
        assertThat(e).isInstanceOf(DiameterErrorAnswerException.class);
        final var answer = (ErrorAnswer) ((DiameterErrorAnswerException) e).getAnswer();
        assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_MISSING_AVP);
        assertThat(answer.getFailedAVP().findAVP(new AVPKey(AVP_IP_SM_GW_REALM, VENDOR_ID_3GPP))).isNotNull();
    }

    @Test
    void itRejectsRealmOnlyServingNodeWithMissingAvp() throws Exception {
        // GIVEN an inbound SAR with a Serving-Node that only carries the realm
        final var in = buildSar(List.of(ipSmGwRealm(IP_SM_GW_REALM)));

        // WHEN
        final var e = catchException(() -> ServingNodeInfo.from(in));

        // THEN the missing name is reported via DIAMETER_MISSING_AVP with Failed-AVP = name key
        assertThat(e).isInstanceOf(DiameterErrorAnswerException.class);
        final var answer = (ErrorAnswer) ((DiameterErrorAnswerException) e).getAnswer();
        assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_MISSING_AVP);
        assertThat(answer.getFailedAVP().findAVP(new AVPKey(AVP_IP_SM_GW_NAME, VENDOR_ID_3GPP))).isNotNull();
    }

    @Test
    void itRejectsBlankIpSmGwNameWithInvalidAvpValue() throws Exception {
        // GIVEN an inbound SAR whose Serving-Node carries a blank IP-SM-GW-Name
        final var in = buildSar(List.of(ipSmGwName("   "), ipSmGwRealm(IP_SM_GW_REALM)));

        // WHEN
        final var e = catchException(() -> ServingNodeInfo.from(in));

        // THEN the blank value is reported via DIAMETER_INVALID_AVP_VALUE
        assertThat(e).isInstanceOf(DiameterErrorAnswerException.class);
        final var answer = (ErrorAnswer) ((DiameterErrorAnswerException) e).getAnswer();
        assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_INVALID_AVP_VALUE);
        assertThat(answer.getFailedAVP().findAVP(new AVPKey(AVP_IP_SM_GW_NAME, VENDOR_ID_3GPP))).isNotNull();
    }

    @Test
    void itRejectsBlankIpSmGwRealmWithInvalidAvpValue() throws Exception {
        // GIVEN an inbound SAR whose Serving-Node carries a blank IP-SM-GW-Realm
        final var in = buildSar(List.of(ipSmGwName(IP_SM_GW_NAME), ipSmGwRealm("")));

        // WHEN
        final var e = catchException(() -> ServingNodeInfo.from(in));

        // THEN the blank value is reported via DIAMETER_INVALID_AVP_VALUE
        assertThat(e).isInstanceOf(DiameterErrorAnswerException.class);
        final var answer = (ErrorAnswer) ((DiameterErrorAnswerException) e).getAnswer();
        assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_INVALID_AVP_VALUE);
        assertThat(answer.getFailedAVP().findAVP(new AVPKey(AVP_IP_SM_GW_REALM, VENDOR_ID_3GPP))).isNotNull();
    }

    @Test
    void itReportsIpSmGwForDirectlyConstructedCompleteRecord() {
        assertThat(new ServingNodeInfo(IP_SM_GW_NAME, IP_SM_GW_REALM).hasIpSmGw()).isTrue();
    }

    @Test
    void itReportsNoIpSmGwForAbsenceRecord() {
        assertThat(new ServingNodeInfo(null, null).hasIpSmGw()).isFalse();
    }

    @Test
    void itReportsNoIpSmGwWhenNameMissing() {
        assertThat(new ServingNodeInfo(null, IP_SM_GW_REALM).hasIpSmGw()).isFalse();
    }

    @Test
    void itReportsNoIpSmGwWhenRealmMissing() {
        assertThat(new ServingNodeInfo(IP_SM_GW_NAME, null).hasIpSmGw()).isFalse();
    }

    @Test
    void itReportsNoIpSmGwWhenNameBlank() {
        assertThat(new ServingNodeInfo("   ", IP_SM_GW_REALM).hasIpSmGw()).isFalse();
    }

    @Test
    void itReportsNoIpSmGwWhenRealmBlank() {
        assertThat(new ServingNodeInfo(IP_SM_GW_NAME, "   ").hasIpSmGw()).isFalse();
    }
}
