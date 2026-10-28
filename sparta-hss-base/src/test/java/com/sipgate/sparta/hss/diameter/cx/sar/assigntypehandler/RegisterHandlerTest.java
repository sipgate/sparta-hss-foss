package com.sipgate.sparta.hss.diameter.cx.sar.assigntypehandler;

import com.sipgate.sparta.diameter._3gpp.cxdx.messages.ServerAssignmentRequest;
import com.sipgate.sparta.diameter.base.core.Answer;
import com.sipgate.sparta.diameter.base.core.Command;
import com.sipgate.sparta.diameter.base.core.DiameterConstants;
import com.sipgate.sparta.diameter.base.core.EndToEndId;
import com.sipgate.sparta.diameter.base.core.ErrorAnswer;
import com.sipgate.sparta.diameter.base.core.HopByHopId;
import com.sipgate.sparta.diameter.base.core.avp.AVP;
import com.sipgate.sparta.diameter.base.core.avp.AVPContainer;
import com.sipgate.sparta.diameter.base.core.avp.AVPKey;
import com.sipgate.sparta.diameter.base.session.DiameterErrorAnswerException;
import com.sipgate.sparta.hss.diameter.cx.sar.ScscfAssignmentChanged;
import com.sipgate.sparta.hss.diameter.cx.sar.identity.PublicIdentity;
import com.sipgate.sparta.hss.diameter.cx.sar.service.ImsService;
import com.sipgate.sparta.hss.event.EventPublisher;
import com.sipgate.sparta.hss.service.accessPolicy.AccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;

import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.AVP_IP_SM_GW_NAME;
import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.AVP_IP_SM_GW_REALM;
import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.AVP_SERVING_NODE;
import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.EXP_RES_DIAMETER_ERROR_USER_UNKNOWN;
import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.VENDOR_ID_3GPP;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.SERVER_ASSIGNMENT_REGISTRATION;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.USER_DATA_ALREADY_AVAILABLE;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.AVP_EXPERIMENTAL_RESULT_CODE;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.AVP_VENDOR_ID;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.RES_DIAMETER_INVALID_AVP_VALUE;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.RES_DIAMETER_MISSING_AVP;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.RES_DIAMETER_SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterHandlerTest {

    private static final String IMSI = "999990000263716";
    private static final String USERNAME = IMSI + "@ims.mnc003.mcc262.3gppnetwork.org";
    private static final String ORIGIN_HOST = "ucn01.dev.sgwl.epc.mnc003.mcc262.3gppnetwork.org";
    private static final String ORIGIN_REALM = "epc.mnc003.mcc262.3gppnetwork.org";
    private static final String IP_SM_GW_NAME = "ucn01.dev.sgwl.epc.mnc003.mcc262.3gppnetwork.org";
    private static final String IP_SM_GW_REALM = "epc.mnc003.mcc262.3gppnetwork.org";
    private static final String ANY_SCSCF = "any-scscf";
    private static final AVPKey KEY_VENDOR_ID = new AVPKey(AVP_VENDOR_ID, 0);
    private static final AVPKey KEY_EXPERIMENTAL_RESULT_CODE = new AVPKey(AVP_EXPERIMENTAL_RESULT_CODE, 0);

    @Mock
    private ImsService imsService;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private PublicIdentity publicIdentity;

    @Mock
    private AccessPolicy accessPolicy;

    private RegisterHandler underTest;

    @BeforeEach
    void setUp() {
        underTest = new RegisterHandler(imsService, eventPublisher, accessPolicy);
    }

    /**
     * Builds an incoming Server-Assignment-Request. A wire-parsed {@code .In} command is immutable,
     * so the request is assembled as an outgoing message, serialized and parsed back — exactly how
     * the stack produces inbound requests at runtime. {@code null} leaves the Serving-Node AVP out.
     */
    private static ServerAssignmentRequest.In buildSar(final List<AVP> servingNodeChildren) throws Exception {
        final var out = new ServerAssignmentRequest.Out();
        out.setUserName(USERNAME);
        out.setServerAssignmentType(SERVER_ASSIGNMENT_REGISTRATION);
        out.setServerName(ANY_SCSCF);
        out.setUserDataAlreadyAvailable(USER_DATA_ALREADY_AVAILABLE);
        out.setOriginHost(ORIGIN_HOST);
        out.setOriginRealm(ORIGIN_REALM);
        if (servingNodeChildren != null) {
            out.addAVP(AVP.create(new AVPKey(AVP_SERVING_NODE, VENDOR_ID_3GPP), servingNodeChildren));
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

    /**
     * Asserts that the answer carries a 3GPP Experimental-Result grouped AVP (Vendor-Id + Experimental-Result-Code)
     * instead of a base Result-Code, as required for vendor-specific 3GPP error codes (RFC 6733 §7.2).
     */
    private static void assertExperimentalErrorAnswer(final Answer answer, final long expectedResultCode) {
        assertThat(answer.getResultCode()).as("no base Result-Code for 3GPP results").isEqualTo(-1L);
        assertThat(answer.isError()).as("an Experimental-Result must not set the E-bit (RFC 6733 §7.6)").isFalse();
        final var experimentalResult = (AVPContainer) answer.findAVP(new AVPKey(DiameterConstants.AVP_EXPERIMENTAL_RESULT, 0));
        assertThat(experimentalResult).as("Experimental-Result grouped AVP").isNotNull();
        assertThat(experimentalResult.findAVP(KEY_VENDOR_ID).getDataAsUnsignedInt()).isEqualTo(VENDOR_ID_3GPP);
        assertThat(experimentalResult.findAVP(KEY_EXPERIMENTAL_RESULT_CODE).getDataAsUnsignedInt())
            .isEqualTo(expectedResultCode);
    }

    @Test
    void itDoesNotStoreIpSmGwWhenServingNodeAbsent() throws Exception {
        // GIVEN a registration SAR without Serving-Node — legitimate absence, e.g. WiFi-only UCN
        final var request = buildSar(null);
        when(accessPolicy.isImsAccessAllowed(IMSI)).thenReturn(Optional.of(true));
        when(imsService.getScscf(IMSI)).thenReturn(Optional.empty());

        // WHEN
        final var answer = underTest.handle(request, publicIdentity);

        // THEN
        assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_SUCCESS);
        verify(imsService, never()).setIpSmGw(any(), any(), any());
        verify(eventPublisher).publish(ScscfAssignmentChanged.ofRegister(IMSI, ANY_SCSCF));
    }

    @Test
    void itStoresIpSmGwWhenServingNodePresent() throws Exception {
        // GIVEN a registration SAR whose Serving-Node carries the UCN as IP-SM-GW
        final var request = buildSar(List.of(ipSmGwName(IP_SM_GW_NAME), ipSmGwRealm(IP_SM_GW_REALM)));
        when(accessPolicy.isImsAccessAllowed(IMSI)).thenReturn(Optional.of(true));
        when(imsService.getScscf(IMSI)).thenReturn(Optional.empty());

        // WHEN
        final var answer = underTest.handle(request, publicIdentity);

        // THEN
        assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_SUCCESS);
        verify(imsService).setIpSmGw(IMSI, IP_SM_GW_NAME, IP_SM_GW_REALM);
        verify(eventPublisher).publish(ScscfAssignmentChanged.ofRegister(IMSI, ANY_SCSCF));
    }

    @Test
    void itPropagatesMissingAvpErrorForServingNodeWithoutRealm() throws Exception {
        // GIVEN a registration SAR whose Serving-Node only carries the name
        final var request = buildSar(List.of(ipSmGwName(IP_SM_GW_NAME)));
        when(accessPolicy.isImsAccessAllowed(IMSI)).thenReturn(Optional.of(true));
        when(imsService.getScscf(IMSI)).thenReturn(Optional.empty());

        // WHEN / THEN the missing realm is reported via DIAMETER_MISSING_AVP with Failed-AVP = realm key
        assertThatThrownBy(() -> underTest.handle(request, publicIdentity))
            .isInstanceOfSatisfying(DiameterErrorAnswerException.class, e -> {
                final var answer = (ErrorAnswer) e.getAnswer();
                assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_MISSING_AVP);
                assertThat(answer.getFailedAVP().findAVP(new AVPKey(AVP_IP_SM_GW_REALM, VENDOR_ID_3GPP))).isNotNull();
            });

        verify(imsService, never()).setIpSmGw(any(), any(), any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void itPropagatesInvalidAvpValueErrorForBlankServingNodeName() throws Exception {
        // GIVEN a registration SAR whose Serving-Node carries a blank IP-SM-GW-Name
        final var request = buildSar(List.of(ipSmGwName("   "), ipSmGwRealm(IP_SM_GW_REALM)));
        when(accessPolicy.isImsAccessAllowed(IMSI)).thenReturn(Optional.of(true));
        when(imsService.getScscf(IMSI)).thenReturn(Optional.empty());

        // WHEN / THEN the blank value is reported via DIAMETER_INVALID_AVP_VALUE with Failed-AVP = name key
        assertThatThrownBy(() -> underTest.handle(request, publicIdentity))
            .isInstanceOfSatisfying(DiameterErrorAnswerException.class, e -> {
                final var answer = (ErrorAnswer) e.getAnswer();
                assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_INVALID_AVP_VALUE);
                assertThat(answer.getFailedAVP().findAVP(new AVPKey(AVP_IP_SM_GW_NAME, VENDOR_ID_3GPP))).isNotNull();
            });

        verify(imsService, never()).setIpSmGw(any(), any(), any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void itAnswersUserUnknownWhenAccessPolicyCannotDecide() throws Exception {
        // GIVEN — a registration SAR for an IMSI the access policy cannot make a decision for
        final var request = buildSar(null);
        when(accessPolicy.isImsAccessAllowed(IMSI)).thenReturn(Optional.empty());

        // WHEN
        final var answer = underTest.handle(request, publicIdentity);

        // THEN — refused as 3GPP Experimental-Result without touching the IMS services
        assertExperimentalErrorAnswer(answer, EXP_RES_DIAMETER_ERROR_USER_UNKNOWN);
        verifyNoInteractions(imsService, eventPublisher);
    }

    @Test
    void itAnswersUserUnknownWhenAccessPolicyDeniesAccess() throws Exception {
        // GIVEN — a registration SAR for an IMSI the access policy denies IMS access for
        final var request = buildSar(null);
        when(accessPolicy.isImsAccessAllowed(IMSI)).thenReturn(Optional.of(false));

        // WHEN
        final var answer = underTest.handle(request, publicIdentity);

        // THEN — refused as 3GPP Experimental-Result without touching the IMS services
        assertExperimentalErrorAnswer(answer, EXP_RES_DIAMETER_ERROR_USER_UNKNOWN);
        verifyNoInteractions(imsService, eventPublisher);
    }
}
