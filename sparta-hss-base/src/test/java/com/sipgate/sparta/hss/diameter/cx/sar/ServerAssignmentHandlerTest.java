package com.sipgate.sparta.hss.diameter.cx.sar;

import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.EXP_RES_DIAMETER_ERROR_IDENTITIES_DONT_MATCH;
import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.EXP_RES_DIAMETER_ERROR_USER_UNKNOWN;
import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.AVP_IP_SM_GW_NAME;
import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.AVP_IP_SM_GW_REALM;
import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.AVP_SERVING_NODE;
import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.VENDOR_ID_3GPP;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.AVP_PUBLIC_IDENTITY;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.EXP_RES_DIAMETER_ERROR_FEATURE_UNSUPPORTED;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.EXP_RES_DIAMETER_SUCCESS_SERVER_NAME_NOT_STORED;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.SERVER_ASSIGNMENT_ADMINISTRATIVE_DEREGISTRATION;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.SERVER_ASSIGNMENT_AUTHENTICATION_FAILURE;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.SERVER_ASSIGNMENT_AUTHENTICATION_TIMEOUT;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.SERVER_ASSIGNMENT_DEREGISTRATION_TOO_MUCH_DATA;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.SERVER_ASSIGNMENT_NO_ASSIGNMENT;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.SERVER_ASSIGNMENT_RE_REGISTRATION;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.SERVER_ASSIGNMENT_REGISTRATION;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.SERVER_ASSIGNMENT_TIMEOUT_DEREGISTRATION;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.SERVER_ASSIGNMENT_TIMEOUT_DEREGISTRATION_STORE_SERVER_NAME;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.SERVER_ASSIGNMENT_UNREGISTERED_USER;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.SERVER_ASSIGNMENT_USER_DEREGISTRATION;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.SERVER_ASSIGNMENT_USER_DEREGISTRATION_STORE_SERVER_NAME;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.USER_DATA_ALREADY_AVAILABLE;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.sipgate.sparta.diameter._3gpp.cxdx.messages.ServerAssignmentAnswer;
import com.sipgate.sparta.diameter._3gpp.cxdx.messages.ServerAssignmentRequest;
import com.sipgate.sparta.diameter.base.core.avp.AVP;
import com.sipgate.sparta.diameter.base.core.avp.AVPContainer;
import com.sipgate.sparta.diameter.base.core.DiameterConstants;
import com.sipgate.sparta.diameter.base.core.Answer;
import com.sipgate.sparta.diameter.base.core.Command;
import com.sipgate.sparta.diameter.base.core.EndToEndId;
import com.sipgate.sparta.diameter.base.core.ErrorAnswer;
import com.sipgate.sparta.diameter.base.core.HopByHopId;
import com.sipgate.sparta.diameter.base.core.avp.AVPKey;
import com.sipgate.sparta.diameter.base.session.DiameterErrorAnswerException;
import com.sipgate.sparta.hss.diameter.cx.sar.service.ImsService;
import com.sipgate.sparta.hss.diameter.cx.sar.service.SimService;
import com.sipgate.sparta.hss.event.EventPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServerAssignmentHandlerTest {

    private static final AVPKey KEY_VENDOR_ID = new AVPKey(AVP_VENDOR_ID, 0);
    private static final AVPKey KEY_EXPERIMENTAL_RESULT_CODE = new AVPKey(AVP_EXPERIMENTAL_RESULT_CODE, 0);
    private static final AVPKey KEY_USER_NAME = new AVPKey(AVP_USER_NAME, 0);
    private static final AVPKey KEY_PUBLIC_IDENTITY = new AVPKey(AVP_PUBLIC_IDENTITY, VENDOR_ID_3GPP);

    private static final String DOMAIN = "ims.mnc000.mcc000.3gppnetwork.org";

    private static final String KNOWN_IMSI = "001001000001234";
    private static final String KNOWN_PRIVATE_IDENTITY = KNOWN_IMSI + "@" + DOMAIN;
    private static final String KNOWN_PUBLIC_IDENTITY_SIP = "sip:" + KNOWN_PRIVATE_IDENTITY;

    private static final String OTHER_KNOWN_IMSI = "001001000005678";
    private static final String OTHER_KNOWN_PRIVATE_IDENTITY = OTHER_KNOWN_IMSI + "@" + DOMAIN;
    private static final String OTHER_KNOWN_PUBLIC_IDENTITY_SIP = "sip:" + OTHER_KNOWN_PRIVATE_IDENTITY;

    private static final String UNKNOWN_IMSI = "999999999999999";
    private static final String UNKNOWN_PRIVATE_IDENTITY = UNKNOWN_IMSI + "@" + DOMAIN;
    private static final String UNKNOWN_PUBLIC_IDENTITY_SIP = "sip:" + UNKNOWN_PRIVATE_IDENTITY;

    private static final String KNOWN_MSISDN = "491234";
    private static final String KNOWN_PUBLIC_IDENTITY_TEL = "tel:+" + KNOWN_MSISDN;

    private static final String OTHER_KNOWN_MSISDN = "495678";
    private static final String OTHER_KNOWN_PUBLIC_IDENTITY_TEL = "tel:+" + OTHER_KNOWN_MSISDN;

    private static final String UNKNOWN_MSISDN = "499999";
    private static final String UNKNOWN_PUBLIC_IDENTITY_TEL = "tel:+" + UNKNOWN_MSISDN;

    private static final String ANY_SCSCF = "any-scscf";

    private static final String ANY_DIAMETER_ORIGIN_HOST = "any-diameter-origin-host";
    private static final String ANY_DIAMETER_ORIGIN_REALM = "any-diameter-origin-realm";

    @Mock
    private SimService simService;

    @Mock
    private ImsService imsService;

    @Spy
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Mock
    private EventPublisher eventPublisher;

    @Captor
    private ArgumentCaptor<ScscfAssignmentChanged> scscfCaptor;

    @InjectMocks
    private ServerAssignmentHandler underTest;

    /**
     * Builds an incoming Server-Assignment-Request. A wire-parsed {@code .In} command is immutable,
     * so the request is assembled as an outgoing message, serialized and parsed back — exactly how
     * the stack produces inbound requests at runtime. {@code null} parameters leave the AVP out.
     */
    private static ServerAssignmentRequest.In buildSar(
        final String username, final List<String> publicIdentities,
        final Integer serverAssignmentType, final String serverName,
        final Integer userDataAlreadyAvailable) throws Exception
    {
        return buildSar(username, publicIdentities, serverAssignmentType, serverName, userDataAlreadyAvailable, null);
    }

    /** Same as {@link #buildSar(String, List, Integer, String, Integer)} plus an optional Serving-Node AVP. */
    private static ServerAssignmentRequest.In buildSar(
        final String username, final List<String> publicIdentities,
        final Integer serverAssignmentType, final String serverName,
        final Integer userDataAlreadyAvailable,
        final List<AVP> servingNodeChildren) throws Exception
    {
        final var out = new ServerAssignmentRequest.Out();
        if (username != null) {
            out.setUserName(username);
        }
        if (publicIdentities != null) {
            out.addAllPublicIdentities(publicIdentities);
        }
        if (serverAssignmentType != null) {
            out.setServerAssignmentType(serverAssignmentType);
        }
        if (serverName != null) {
            out.setServerName(serverName);
        }
        if (userDataAlreadyAvailable != null) {
            out.setUserDataAlreadyAvailable(userDataAlreadyAvailable);
        }
        out.setOriginHost(ANY_DIAMETER_ORIGIN_HOST);
        out.setOriginRealm(ANY_DIAMETER_ORIGIN_REALM);
        if (servingNodeChildren != null) {
            out.addAVP(AVP.create(new AVPKey(AVP_SERVING_NODE, VENDOR_ID_3GPP), servingNodeChildren));
        }

        final var baos = new ByteArrayOutputStream();
        out.writeTo(new DataOutputStream(baos), new HopByHopId(1), new EndToEndId(2));
        return (ServerAssignmentRequest.In) Command.parseMessage(ByteBuffer.wrap(baos.toByteArray()));
    }

    private static ServerAssignmentRequest.In buildRegisterSar(final String username, final String publicIdentity) throws Exception {
        return buildSar(username, publicIdentity == null ? null : List.of(publicIdentity), SERVER_ASSIGNMENT_REGISTRATION, ANY_SCSCF, null);
    }


    /** Experimental-Result on a regular answer — only correct for 3GPP success codes like 2004. */
    private static void assertExperimentalResult(final ServerAssignmentAnswer.Out answer, final long expectedResultCode) {
        assertThat(answer.isError()).as("an Experimental-Result must not set the E-bit (RFC 6733 §7.6)").isFalse();
        final var experimentalResult = (AVPContainer) answer.findAVP(new AVPKey(DiameterConstants.AVP_EXPERIMENTAL_RESULT, 0));
        assertThat(experimentalResult).as("Experimental-Result grouped AVP").isNotNull();
        assertThat(experimentalResult.findAVP(KEY_VENDOR_ID).getDataAsUnsignedInt()).isEqualTo((long) VENDOR_ID_3GPP);
        assertThat(experimentalResult.findAVP(KEY_EXPERIMENTAL_RESULT_CODE).getDataAsUnsignedInt())
            .isEqualTo(expectedResultCode);
    }

    private static Answer unwrapErrorAnswer(final CompletableFuture<?> future) {
        return ((DiameterErrorAnswerException) future.exceptionNow()).getAnswer();
    }

    private static void assertExperimentalErrorAnswer(final Answer answer, final long expectedResultCode) {
        assertThat(answer.getResultCode()).as("no base Result-Code for 3GPP results").isEqualTo(-1L);
        assertThat(answer.isError()).as("an Experimental-Result must not set the E-bit (RFC 6733 §7.6)").isFalse();
        final var experimentalResult = (AVPContainer) answer.findAVP(new AVPKey(DiameterConstants.AVP_EXPERIMENTAL_RESULT, 0));
        assertThat(experimentalResult).as("Experimental-Result grouped AVP").isNotNull();
        assertThat(experimentalResult.findAVP(KEY_VENDOR_ID).getDataAsUnsignedInt()).isEqualTo(VENDOR_ID_3GPP);
        assertThat(experimentalResult.findAVP(KEY_EXPERIMENTAL_RESULT_CODE).getDataAsUnsignedInt())
            .isEqualTo(expectedResultCode);
    }

    /** Asserts a MISSING_AVP error answer whose Failed-AVP carries the missing AVP with zero-length data. */
    private static void assertMissingAvp(final Answer answer, final AVPKey expectedMissingAvp) {
        assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_MISSING_AVP);
        final var failedAvp = ((ErrorAnswer) answer).getFailedAVP();
        assertThat(failedAvp).as("Failed-AVP grouped AVP").isNotNull();
        final var missing = failedAvp.findAVP(expectedMissingAvp);
        assertThat(missing).as("missing AVP inside Failed-AVP").isNotNull();
        assertThat(missing.getData()).isEmpty();
    }

    /* Starting from here, we test behaviour as outlined in TS 129.228 v16.1.0 Release 16 Section 6.1.2.1 */

    @Test
    void itRejectsUnparsablePublicIdentity() throws Exception {
        // GIVEN
        final var request = buildRegisterSar(KNOWN_PRIVATE_IDENTITY, "neither-sip-nor-tel-uri");

        // WHEN
        final var answer = unwrapErrorAnswer(underTest.handle(request));

        // THEN
        assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_INVALID_AVP_VALUE);
    }

    @Test
    void itRejectsMissingServerName() throws Exception {
        // GIVEN
        final var request = buildSar(KNOWN_PRIVATE_IDENTITY, List.of(KNOWN_PUBLIC_IDENTITY_SIP), SERVER_ASSIGNMENT_REGISTRATION, null, null);
        when(simService.getMsisdnByImsi(KNOWN_IMSI)).thenReturn(Optional.of(KNOWN_MSISDN));

        // WHEN
        final var answer = unwrapErrorAnswer(underTest.handle(request));

        // THEN
        assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_MISSING_AVP);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = SERVER_ASSIGNMENT_NO_ASSIGNMENT)
    void itRejectsMissingOrUnsupportedServerAssignmentType(final Integer serverAssignmentType) throws Exception {
        // GIVEN
        final var request = buildSar(KNOWN_PRIVATE_IDENTITY, List.of(KNOWN_PUBLIC_IDENTITY_SIP), serverAssignmentType, ANY_SCSCF, null);
        when(simService.getMsisdnByImsi(KNOWN_IMSI)).thenReturn(Optional.of(KNOWN_MSISDN));

        // WHEN
        final var answer = unwrapErrorAnswer(underTest.handle(request));

        // THEN
        assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_UNABLE_TO_COMPLY);
    }

    @Test
    void itRejectsMissingPublicIdentities() throws Exception {
        // GIVEN
        final var request = buildRegisterSar(KNOWN_PRIVATE_IDENTITY, null);

        // WHEN
        final var answer = unwrapErrorAnswer(underTest.handle(request));

        // THEN
        assertMissingAvp(answer, KEY_PUBLIC_IDENTITY);
    }

    @Test
    void itRejectsMissingPrivateIdentities() throws Exception {
        // GIVEN
        final var request = buildRegisterSar(null, KNOWN_PUBLIC_IDENTITY_SIP);

        // WHEN
        final var answer = unwrapErrorAnswer(underTest.handle(request));

        // THEN
        assertMissingAvp(answer, KEY_USER_NAME);
    }

    /**
     * RFC 6733 §7.5: the Failed-AVP refers to the first AVP processing error encountered,
     * so with both identities missing only the User-Name is reported.
     */
    @Test
    void itReportsOnlyTheFirstMissingAvp() throws Exception {
        // GIVEN
        final var request = buildRegisterSar(null, null);

        // WHEN
        final var answer = unwrapErrorAnswer(underTest.handle(request));

        // THEN
        assertMissingAvp(answer, KEY_USER_NAME);
        assertThat(((ErrorAnswer) answer).getFailedAVP().findAVP(KEY_PUBLIC_IDENTITY)).isNull();
    }

    /**
     * 1. Check that the Public Identity and Private Identity exist in the HSS. If not Experimental-Result-Code shall be
     * set to DIAMETER_ERROR_USER_UNKNOWN
     */
    @Test
    void itRejectsUnknownPrivateIdentities() throws Exception {
        // GIVEN
        final var request = buildRegisterSar(UNKNOWN_PRIVATE_IDENTITY, KNOWN_PUBLIC_IDENTITY_SIP);
        when(simService.getMsisdnByImsi(KNOWN_IMSI)).thenReturn(Optional.of(KNOWN_MSISDN));
        when(simService.isImsiKnown(UNKNOWN_IMSI)).thenReturn(false);

        // WHEN
        final var answer = unwrapErrorAnswer(underTest.handle(request));

        // THEN
        assertExperimentalErrorAnswer(answer, EXP_RES_DIAMETER_ERROR_USER_UNKNOWN);
    }

    @Test
    void itRejectsUnknownPublicIdentitiesForImsis() throws Exception {
        // GIVEN
        final var request = buildRegisterSar(KNOWN_PRIVATE_IDENTITY, UNKNOWN_PUBLIC_IDENTITY_SIP);
        when(simService.getMsisdnByImsi(UNKNOWN_IMSI)).thenReturn(Optional.empty());
        when(simService.isImsiKnown(KNOWN_IMSI)).thenReturn(true);

        // WHEN
        final var answer = unwrapErrorAnswer(underTest.handle(request));

        // THEN
        assertExperimentalErrorAnswer(answer, EXP_RES_DIAMETER_ERROR_USER_UNKNOWN);
    }

    @Test
    void itRejectsUnknownPublicIdentitiesForMsisdns() throws Exception {
        // GIVEN
        final var request = buildRegisterSar(KNOWN_PRIVATE_IDENTITY, UNKNOWN_PUBLIC_IDENTITY_TEL);
        when(simService.isImsiKnown(KNOWN_IMSI)).thenReturn(true);
        when(simService.isMsisdnKnown(UNKNOWN_MSISDN)).thenReturn(false);

        // WHEN
        final var answer = unwrapErrorAnswer(underTest.handle(request));

        // THEN
        assertExperimentalErrorAnswer(answer, EXP_RES_DIAMETER_ERROR_USER_UNKNOWN);
    }

    /**
     * 2. The HSS may check whether the Private and Public Identities received in the request are associated in the HSS.
     * If not Experimental-Result-Code shall be set to DIAMETER_ERROR_IDENTITIES_DONT_MATCH.
     */
    @Test
    void itRejectsMismatchOfIdentitiesWithImsiAsPublicIdentity() throws Exception {
        // GIVEN
        final var request = buildRegisterSar(KNOWN_PRIVATE_IDENTITY, OTHER_KNOWN_PUBLIC_IDENTITY_SIP);
        when(simService.getMsisdnByImsi(OTHER_KNOWN_IMSI)).thenReturn(Optional.of(OTHER_KNOWN_MSISDN));
        when(simService.isImsiKnown(KNOWN_IMSI)).thenReturn(true);

        // WHEN
        final var answer = unwrapErrorAnswer(underTest.handle(request));

        // THEN
        assertExperimentalErrorAnswer(answer, EXP_RES_DIAMETER_ERROR_IDENTITIES_DONT_MATCH);
    }

    @Test
    void itRejectsMismatchOfIdentitiesWithMsisdnAsPublicIdentity() throws Exception {
        // GIVEN
        final var request = buildRegisterSar(KNOWN_PRIVATE_IDENTITY, OTHER_KNOWN_PUBLIC_IDENTITY_TEL);
        when(simService.isImsiKnown(KNOWN_IMSI)).thenReturn(true);
        when(simService.isMsisdnKnown(OTHER_KNOWN_MSISDN)).thenReturn(true);
        when(simService.getMsisdnByImsi(KNOWN_IMSI)).thenReturn(Optional.of(KNOWN_MSISDN));

        // WHEN
        final var answer = unwrapErrorAnswer(underTest.handle(request));

        // THEN
        assertExperimentalErrorAnswer(answer, EXP_RES_DIAMETER_ERROR_IDENTITIES_DONT_MATCH);
    }

    /**
     * 3. If more than one Public-Identity AVP is present and the Server-Assignment-Type is one of the values defined in
     * Table 6.1.2.1 as applying for only one identity, then the Result Code shall be set to
     * DIAMETER_AVP_OCCURS_TOO_MANY_TIMES and no user information shall be returned.
     */
    @Test
    void itRejectsTooManyPublicIdentities() throws Exception {
        // GIVEN
        final var request = buildSar(
            KNOWN_PRIVATE_IDENTITY,
            List.of(KNOWN_PUBLIC_IDENTITY_SIP, KNOWN_PUBLIC_IDENTITY_TEL),
            SERVER_ASSIGNMENT_REGISTRATION, ANY_SCSCF, null);
        when(simService.getMsisdnByImsi(KNOWN_IMSI)).thenReturn(Optional.of(KNOWN_MSISDN));

        // WHEN
        final var answer = unwrapErrorAnswer(underTest.handle(request));

        // THEN: Failed-AVP carries a copy of the first instance that exceeded the allowed single occurrence
        assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_AVP_OCCURS_TOO_MANY_TIMES);
        final var failedAvp = ((ErrorAnswer) answer).getFailedAVP();
        assertThat(failedAvp).as("Failed-AVP grouped AVP").isNotNull();
        assertThat(failedAvp.findAVP(KEY_PUBLIC_IDENTITY).getDataAsString()).isEqualTo(KNOWN_PUBLIC_IDENTITY_TEL);
    }

    /**
     * 5. Check the Server Assignment Type value received in the request […]
     */
    @ParameterizedTest
    @ValueSource(ints = {SERVER_ASSIGNMENT_REGISTRATION, SERVER_ASSIGNMENT_RE_REGISTRATION})
    void itSendsUserDataWithImsiAsPublicIdentity(final int serverAssignmentType) throws Exception {
        // GIVEN
        final var request = buildSar(KNOWN_PRIVATE_IDENTITY, List.of(KNOWN_PUBLIC_IDENTITY_SIP), serverAssignmentType, ANY_SCSCF, null);

        when(simService.isImsiKnown(KNOWN_IMSI)).thenReturn(true);
        when(simService.getMsisdnByImsi(KNOWN_IMSI)).thenReturn(Optional.of(KNOWN_MSISDN));
        when(imsService.getScscf(KNOWN_IMSI)).thenReturn(Optional.empty());
        doNothing().when(eventPublisher).publish(scscfCaptor.capture());

        final var anyUserData = "any-user-data";
        when(imsService.createUserData(KNOWN_PRIVATE_IDENTITY, KNOWN_MSISDN)).thenReturn(anyUserData);

        // WHEN
        final var answer = underTest.handle(request).join();

        // THEN
        assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_SUCCESS);
        assertThat(new String(answer.getUserData(), UTF_8)).isEqualTo(anyUserData);
        assertThat(answer.getLooseRouteIndication()).isEqualTo(1);

        verify(imsService).setScscf(KNOWN_IMSI, ANY_SCSCF, ANY_DIAMETER_ORIGIN_HOST, ANY_DIAMETER_ORIGIN_REALM);
        assertThat(scscfCaptor.getValue())
                .isEqualTo(ScscfAssignmentChanged.ofRegister(KNOWN_IMSI, ANY_SCSCF));
    }

    @ParameterizedTest
    @ValueSource(ints = {SERVER_ASSIGNMENT_REGISTRATION, SERVER_ASSIGNMENT_RE_REGISTRATION})
    void itSendsUserDataWithMsisdnAsPublicIdentity(final int serverAssignmentType) throws Exception {
        // GIVEN
        final var request = buildSar(KNOWN_PRIVATE_IDENTITY, List.of(KNOWN_PUBLIC_IDENTITY_TEL), serverAssignmentType, ANY_SCSCF, null);

        when(simService.isImsiKnown(KNOWN_IMSI)).thenReturn(true);
        when(simService.isMsisdnKnown(KNOWN_MSISDN)).thenReturn(true);
        when(simService.getMsisdnByImsi(KNOWN_IMSI)).thenReturn(Optional.of(KNOWN_MSISDN));
        when(imsService.getScscf(KNOWN_IMSI)).thenReturn(Optional.empty());
        doNothing().when(eventPublisher).publish(scscfCaptor.capture());

        final var anyUserData = "any-user-data";
        when(imsService.createUserData(KNOWN_PRIVATE_IDENTITY, KNOWN_MSISDN)).thenReturn(anyUserData);

        // WHEN
        final var answer = underTest.handle(request).join();

        // THEN
        assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_SUCCESS);
        assertThat(new String(answer.getUserData(), UTF_8)).isEqualTo(anyUserData);
        assertThat(answer.getLooseRouteIndication()).isEqualTo(1);
        assertThat(scscfCaptor.getValue())
                .isEqualTo(ScscfAssignmentChanged.ofRegister(KNOWN_IMSI, ANY_SCSCF));

        verify(imsService).setScscf(KNOWN_IMSI, ANY_SCSCF, ANY_DIAMETER_ORIGIN_HOST, ANY_DIAMETER_ORIGIN_REALM);
    }

    @ParameterizedTest
    @ValueSource(ints = {SERVER_ASSIGNMENT_REGISTRATION, SERVER_ASSIGNMENT_RE_REGISTRATION})
    void itDoesNotSendUserDataWhenUserDataAlreadyAvailable(final int serverAssignmentType) throws Exception {
        // GIVEN
        final var request = buildSar(
            KNOWN_PRIVATE_IDENTITY, List.of(KNOWN_PUBLIC_IDENTITY_SIP),
            serverAssignmentType, ANY_SCSCF, USER_DATA_ALREADY_AVAILABLE);

        when(simService.isImsiKnown(KNOWN_IMSI)).thenReturn(true);
        when(simService.getMsisdnByImsi(KNOWN_IMSI)).thenReturn(Optional.of(KNOWN_MSISDN));
        when(imsService.getScscf(KNOWN_IMSI)).thenReturn(Optional.empty());
        doNothing().when(eventPublisher).publish(scscfCaptor.capture());

        // WHEN
        final var answer = underTest.handle(request).join();

        // THEN
        assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_SUCCESS);
        assertThat(answer.getUserData()).isNull();

        verify(imsService).setScscf(KNOWN_IMSI, ANY_SCSCF, ANY_DIAMETER_ORIGIN_HOST, ANY_DIAMETER_ORIGIN_REALM);
        verify(imsService, never()).createUserData(any(), any());
        assertThat(scscfCaptor.getValue())
                .isEqualTo(ScscfAssignmentChanged.ofRegister(KNOWN_IMSI, ANY_SCSCF));
    }

    @Test
    void itRejectsUnregisteredUser() throws Exception {
        // GIVEN
        final var request = buildSar(KNOWN_PRIVATE_IDENTITY, List.of(KNOWN_PUBLIC_IDENTITY_SIP), SERVER_ASSIGNMENT_UNREGISTERED_USER, ANY_SCSCF, null);
        when(simService.getMsisdnByImsi(KNOWN_IMSI)).thenReturn(Optional.of(KNOWN_MSISDN));

        // WHEN
        final var answer = unwrapErrorAnswer(underTest.handle(request));

        // THEN
        assertExperimentalErrorAnswer(answer, EXP_RES_DIAMETER_ERROR_FEATURE_UNSUPPORTED);
    }

    private static Stream<Arguments> provideDeregistrationsAndResultCodes() {
        return Stream.of(
                Arguments.of(SERVER_ASSIGNMENT_TIMEOUT_DEREGISTRATION, RES_DIAMETER_SUCCESS, false),
                Arguments.of(SERVER_ASSIGNMENT_USER_DEREGISTRATION, RES_DIAMETER_SUCCESS, false),
                Arguments.of(SERVER_ASSIGNMENT_ADMINISTRATIVE_DEREGISTRATION, RES_DIAMETER_SUCCESS, false),
                Arguments.of(SERVER_ASSIGNMENT_DEREGISTRATION_TOO_MUCH_DATA, RES_DIAMETER_SUCCESS, false),
                // 2004 ist ein 3GPP-Code und steht deshalb im grouped Experimental-Result statt im Result-Code
                Arguments.of(SERVER_ASSIGNMENT_TIMEOUT_DEREGISTRATION_STORE_SERVER_NAME, EXP_RES_DIAMETER_SUCCESS_SERVER_NAME_NOT_STORED, true),
                Arguments.of(SERVER_ASSIGNMENT_USER_DEREGISTRATION_STORE_SERVER_NAME, EXP_RES_DIAMETER_SUCCESS_SERVER_NAME_NOT_STORED, true)
        );
    }

    @ParameterizedTest
    @MethodSource("provideDeregistrationsAndResultCodes")
    void itDeregisteresUsers(final int serverAssignmentType, final long expectedResultCode, final boolean isExperimental) throws Exception {
        // GIVEN
        final var request = buildSar(KNOWN_PRIVATE_IDENTITY, List.of(KNOWN_PUBLIC_IDENTITY_SIP), serverAssignmentType, ANY_SCSCF, null);

        when(simService.isImsiKnown(KNOWN_IMSI)).thenReturn(true);
        when(simService.getMsisdnByImsi(KNOWN_IMSI)).thenReturn(Optional.of(KNOWN_MSISDN));
        when(imsService.getScscf(KNOWN_IMSI)).thenReturn(Optional.of(ANY_SCSCF));
        doNothing().when(eventPublisher).publish(scscfCaptor.capture());

        // WHEN
        final var answer = underTest.handle(request).join();

        // THEN
        if (isExperimental) {
            assertExperimentalResult(answer, expectedResultCode);
        } else {
            assertThat(answer.getResultCode()).isEqualTo(expectedResultCode);
        }
        assertThat(answer.getUserData()).isNull();

        verify(imsService).getScscf(KNOWN_IMSI);
        verify(imsService).clearScscf(KNOWN_IMSI);
        verify(imsService).clearIpSmGw(KNOWN_IMSI);
        verifyNoMoreInteractions(imsService);
        assertThat(scscfCaptor.getValue())
                .isEqualTo(ScscfAssignmentChanged.ofUnregister(KNOWN_IMSI));
    }

    @ParameterizedTest
    @ValueSource(ints = {SERVER_ASSIGNMENT_AUTHENTICATION_FAILURE, SERVER_ASSIGNMENT_AUTHENTICATION_TIMEOUT})
    void itReturnsSuccessAndDoesNothingForAuthenticationFailures(final int serverAssignmentType) throws Exception {
        // GIVEN
        final var request = buildSar(KNOWN_PRIVATE_IDENTITY, List.of(KNOWN_PUBLIC_IDENTITY_SIP), serverAssignmentType, ANY_SCSCF, null);

        when(simService.isImsiKnown(KNOWN_IMSI)).thenReturn(true);
        when(simService.getMsisdnByImsi(KNOWN_IMSI)).thenReturn(Optional.of(KNOWN_MSISDN));

        // WHEN
        final var answer = underTest.handle(request).join();

        // THEN
        verifyNoInteractions(imsService, eventPublisher);
        assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_SUCCESS);
        assertThat(answer.getUserData()).isNull();
    }

    private static List<AVP> servingNodeWithIpSmGw() {
        return List.of(
            AVP.create(new AVPKey(AVP_IP_SM_GW_NAME, VENDOR_ID_3GPP), "ucn01.dev.sgwl.epc.mnc003.mcc262.3gppnetwork.org"),
            AVP.create(new AVPKey(AVP_IP_SM_GW_REALM, VENDOR_ID_3GPP), "epc.mnc003.mcc262.3gppnetwork.org"));
    }

    @Test
    void itStoresIpSmGwWhenServingNodePresent() throws Exception {
        // GIVEN a registration SAR whose Serving-Node carries the UCN as IP-SM-GW
        final var request = buildSar(
            KNOWN_PRIVATE_IDENTITY, List.of(KNOWN_PUBLIC_IDENTITY_SIP),
            SERVER_ASSIGNMENT_REGISTRATION, ANY_SCSCF, null, servingNodeWithIpSmGw());

        when(simService.isImsiKnown(KNOWN_IMSI)).thenReturn(true);
        when(simService.getMsisdnByImsi(KNOWN_IMSI)).thenReturn(Optional.of(KNOWN_MSISDN));
        when(imsService.getScscf(KNOWN_IMSI)).thenReturn(Optional.empty());
        when(imsService.createUserData(KNOWN_PRIVATE_IDENTITY, KNOWN_MSISDN)).thenReturn("any-user-data");

        // WHEN
        final var answer = underTest.handle(request).join();

        // THEN
        assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_SUCCESS);
        verify(imsService).setIpSmGw(KNOWN_IMSI, "ucn01.dev.sgwl.epc.mnc003.mcc262.3gppnetwork.org", "epc.mnc003.mcc262.3gppnetwork.org");
    }

    @Test
    void itDoesNotStoreIpSmGwWhenServingNodeAbsent() throws Exception {
        // GIVEN a registration SAR without Serving-Node
        final var request = buildSar(
            KNOWN_PRIVATE_IDENTITY, List.of(KNOWN_PUBLIC_IDENTITY_SIP),
            SERVER_ASSIGNMENT_REGISTRATION, ANY_SCSCF, null, null);

        when(simService.isImsiKnown(KNOWN_IMSI)).thenReturn(true);
        when(simService.getMsisdnByImsi(KNOWN_IMSI)).thenReturn(Optional.of(KNOWN_MSISDN));
        when(imsService.getScscf(KNOWN_IMSI)).thenReturn(Optional.empty());
        when(imsService.createUserData(KNOWN_PRIVATE_IDENTITY, KNOWN_MSISDN)).thenReturn("any-user-data");

        // WHEN
        final var answer = underTest.handle(request).join();

        // THEN
        assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_SUCCESS);
        verify(imsService, never()).setIpSmGw(any(), any(), any());
    }

    @Test
    void itClearsIpSmGwOnMatchingDeregistration() throws Exception {
        // GIVEN a deregistration SAR from the currently assigned S-CSCF
        final var request = buildSar(
            KNOWN_PRIVATE_IDENTITY, List.of(KNOWN_PUBLIC_IDENTITY_SIP),
            SERVER_ASSIGNMENT_USER_DEREGISTRATION, ANY_SCSCF, null);

        when(simService.isImsiKnown(KNOWN_IMSI)).thenReturn(true);
        when(simService.getMsisdnByImsi(KNOWN_IMSI)).thenReturn(Optional.of(KNOWN_MSISDN));
        when(imsService.getScscf(KNOWN_IMSI)).thenReturn(Optional.of(ANY_SCSCF));

        // WHEN
        final var answer = underTest.handle(request).join();

        // THEN
        assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_SUCCESS);
        verify(imsService).clearScscf(KNOWN_IMSI);
        verify(imsService).clearIpSmGw(KNOWN_IMSI);
    }

    @Test
    void itKeepsIpSmGwOnScscfMismatch() throws Exception {
        // GIVEN a deregistration SAR from an S-CSCF that is not the currently assigned one
        final var request = buildSar(
            KNOWN_PRIVATE_IDENTITY, List.of(KNOWN_PUBLIC_IDENTITY_SIP),
            SERVER_ASSIGNMENT_USER_DEREGISTRATION, "other-scscf", null);

        when(simService.isImsiKnown(KNOWN_IMSI)).thenReturn(true);
        when(simService.getMsisdnByImsi(KNOWN_IMSI)).thenReturn(Optional.of(KNOWN_MSISDN));
        when(imsService.getScscf(KNOWN_IMSI)).thenReturn(Optional.of(ANY_SCSCF));

        // WHEN
        final var answer = underTest.handle(request).join();

        // THEN
        assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_SUCCESS);
        verify(imsService, never()).clearScscf(any());
        verify(imsService, never()).clearIpSmGw(any());
    }
}
