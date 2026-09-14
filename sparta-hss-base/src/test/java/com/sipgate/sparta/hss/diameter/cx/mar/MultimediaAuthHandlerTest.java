package com.sipgate.sparta.hss.diameter.cx.mar;

import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.EXP_RES_DIAMETER_ERROR_USER_UNKNOWN;
import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.VENDOR_ID_3GPP;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.AVP_CONFIDENTIALITY_KEY;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.AVP_INTEGRITY_KEY;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.AVP_REASON_CODE;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.AVP_REASON_INFO;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.AVP_SIP_AUTHENTICATE;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.AVP_SIP_AUTH_DATA_ITEM;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.AVP_SIP_AUTHENTICATION_SCHEME;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.AVP_SIP_AUTHORIZATION;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.AVP_SIP_ITEM_NUMBER;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.EXP_RES_DIAMETER_ERROR_AUTH_SCHEME_NOT_SUPPORTED;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.REASON_CODE_NEW_SERVER_ASSIGNED;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.*;
import static jakarta.xml.bind.DatatypeConverter.printHexBinary;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants;
import com.sipgate.sparta.diameter._3gpp.cxdx.messages.MultimediaAuthAnswer;
import com.sipgate.sparta.diameter._3gpp.cxdx.messages.MultimediaAuthRequest;
import com.sipgate.sparta.diameter._3gpp.cxdx.messages.RegistrationTerminationRequest;
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
import com.sipgate.sparta.hss.diameter.client.DiameterSessions;
import com.sipgate.sparta.hss.diameter.common.Factory;
import com.sipgate.sparta.hss.diameter.common.auth.MilenageAuthenticator;
import com.sipgate.sparta.hss.diameter.common.auth.StoredKeyFormat;
import com.sipgate.sparta.hss.diameter.common.auth.RandomGenerator;
import com.sipgate.sparta.hss.diameter.cx.rtr.RegistrationTerminationEvent;
import com.sipgate.sparta.hss.persistence.ImsiScscfDao;
import com.sipgate.sparta.hss.persistence.SimDao;
import com.sipgate.sparta.hss.persistence.entities.ImsiScscf;
import com.sipgate.sparta.hss.persistence.entities.Msisdn;
import com.sipgate.sparta.hss.persistence.entities.Sim;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.sipgate.sparta.hss.event.EventPublisher;

@ExtendWith(MockitoExtension.class)
class MultimediaAuthHandlerTest {

    private static final AVPKey KEY_SIP_AUTHENTICATION_SCHEME = new AVPKey(AVP_SIP_AUTHENTICATION_SCHEME, VENDOR_ID_3GPP);
    private static final AVPKey KEY_AUTH_APPLICATION_ID = new AVPKey(AVP_AUTH_APPLICATION_ID, 0);
    private static final AVPKey KEY_SIP_AUTHORIZATION = new AVPKey(AVP_SIP_AUTHORIZATION, VENDOR_ID_3GPP);
    private static final AVPKey KEY_CONFIDENTIALITY_KEY = new AVPKey(AVP_CONFIDENTIALITY_KEY, VENDOR_ID_3GPP);
    private static final AVPKey KEY_INTEGRITY_KEY = new AVPKey(AVP_INTEGRITY_KEY, VENDOR_ID_3GPP);
    private static final AVPKey KEY_SIP_AUTHENTICATE = new AVPKey(AVP_SIP_AUTHENTICATE, VENDOR_ID_3GPP);
    private static final AVPKey KEY_SIP_ITEM_NUMBER = new AVPKey(AVP_SIP_ITEM_NUMBER, VENDOR_ID_3GPP);
    private static final AVPKey KEY_SIP_AUTH_DATA_ITEM = new AVPKey(AVP_SIP_AUTH_DATA_ITEM, VENDOR_ID_3GPP);
    private static final AVPKey KEY_USER_NAME = new AVPKey(AVP_USER_NAME, 0);
    private static final AVPKey KEY_VENDOR_ID = new AVPKey(AVP_VENDOR_ID, 0);
    private static final AVPKey KEY_EXPERIMENTAL_RESULT_CODE = new AVPKey(AVP_EXPERIMENTAL_RESULT_CODE, 0);

    private MultimediaAuthHandler underTest;

    @Mock
    private RandomGenerator randomGenerator;

    @Mock
    private SimDao simDao;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private ImsiScscfDao imsiScscfDao;

    @BeforeEach
    void setUp() {
        final var authenticator = new MilenageAuthenticator(randomGenerator, new SimpleMeterRegistry(), StoredKeyFormat.OP);
        final var diameterSessions = new DiameterSessions(Factory.nodeConfig("origin-host", "origin-realm"));
        underTest = new MultimediaAuthHandler(simDao, imsiScscfDao, authenticator, eventPublisher, diameterSessions);
    }

    public static Stream<Arguments> provideRtrCase() {
        return Stream.of(
            Arguments.of("previous-scscf", "previous-diameter-host", "other-diameter-realm"),
            Arguments.of("previous-scscf", "other-diameter-host", "previous-diameter-realm"),
            Arguments.of("other-scscf", "previous-diameter-host", "previous-diameter-realm")
        );
    }

    private static MultimediaAuthRequest.In buildMar(final String username, final String authScheme) throws Exception {
        return buildMar(username, authScheme, null, null, null);
    }

    /**
     * Builds an incoming Multimedia-Auth-Request carrying a SIP-Auth-Data-Item. When {@code authScheme}
     * is {@code null} the grouped AVP is present but holds no SIP-Authentication-Scheme.
     * <p>
     * A wire-parsed {@code .In} command is immutable, so the request is assembled as an outgoing
     * message, serialized and parsed back — exactly how the stack produces inbound requests at runtime.
     */
    private static MultimediaAuthRequest.In buildMar(
        final String username, final String authScheme,
        final String serverName, final String originHost, final String originRealm) throws Exception
    {
        return buildMar(username, authScheme, serverName, originHost, originRealm, null);
    }

    /**
     * Builds an incoming Multimedia-Auth-Request carrying a SIP-Auth-Data-Item. When {@code authScheme}
     * is {@code null} the grouped AVP is present but holds no SIP-Authentication-Scheme.
     * When {@code sipAuthorization} is non-null it is added as a SIP-Authorization AVP (RAND&#8214;AUTS).
     * <p>
     * A wire-parsed {@code .In} command is immutable, so the request is assembled as an outgoing
     * message, serialized and parsed back — exactly how the stack produces inbound requests at runtime.
     */
    private static MultimediaAuthRequest.In buildMar(
        final String username, final String authScheme,
        final String serverName, final String originHost, final String originRealm,
        final byte[] sipAuthorization) throws Exception
    {
        final var out = new MultimediaAuthRequest.Out();
        if (username != null) {
            out.setUserName(username);
        }
        if (serverName != null) {
            out.setServerName(serverName);
        }
        if (originHost != null) {
            out.setOriginHost(originHost);
        }
        if (originRealm != null) {
            out.setOriginRealm(originRealm);
        }

        final List<AVP> sipAuthDataItem = new ArrayList<>();
        if (authScheme != null) {
            sipAuthDataItem.add(AVP.create(KEY_SIP_AUTHENTICATION_SCHEME, authScheme));
        }
        if (sipAuthorization != null) {
            sipAuthDataItem.add(AVP.create(KEY_SIP_AUTHORIZATION, sipAuthorization));
        }
        out.setSipAuthDataItem(sipAuthDataItem);

        return parseAsIncoming(out);
    }

    private static MultimediaAuthRequest.In buildMarWithoutSipAuthDataItem(final String username) throws Exception {
        final var out = new MultimediaAuthRequest.Out();
        out.setUserName(username);
        return parseAsIncoming(out);
    }

    private static MultimediaAuthRequest.In parseAsIncoming(final MultimediaAuthRequest.Out out) throws Exception {
        final var baos = new ByteArrayOutputStream();
        out.writeTo(new DataOutputStream(baos), new HopByHopId(1), new EndToEndId(2));
        return (MultimediaAuthRequest.In) Command.parseMessage(ByteBuffer.wrap(baos.toByteArray()));
    }

    private static void assertSuccessfulQuintuplets(
        final MultimediaAuthAnswer.Out answer, final String expectedUsername, final String expectedMsisdn)
    {
        assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_SUCCESS);
        assertThat(answer.getUserName()).isEqualTo(expectedUsername);
        assertThat(answer.getPublicIdentity()).isEqualTo("tel:+" + expectedMsisdn);
        assertThat(answer.getSipNumberAuthItems()).isEqualTo(1L);

        final var sipAuthDataItems = answer.getSipAuthDataItems();
        assertThat(sipAuthDataItems).hasSize(1);
        final var item = sipAuthDataItems.getFirst();

        assertThat(printHexBinary(item.findAVP(KEY_CONFIDENTIALITY_KEY).getData()))
            .isEqualTo("1BC700F2A0ADAEE39A32A389D1956DCC");
        assertThat(printHexBinary(item.findAVP(KEY_INTEGRITY_KEY).getData()))
            .isEqualTo("0BDD1EE29E8BBBC525998B4C15114FA7");
        // first part of Authenticate is randomValue
        assertThat(printHexBinary(item.findAVP(KEY_SIP_AUTHENTICATE).getData()))
            .isEqualTo("72616E646F6D2D76616C7565313233343D23D679A91F00002DDC816064C97892");
        assertThat(printHexBinary(item.findAVP(KEY_SIP_AUTHORIZATION).getData()))
            .isEqualTo("69211C8CED175084");
        assertThat(item.findAVP(KEY_SIP_AUTHENTICATION_SCHEME).getDataAsString()).isEqualTo("Digest-AKAv1-MD5");
        assertThat(item.findAVP(KEY_SIP_ITEM_NUMBER).getDataAsInt()).isZero();
    }

    private static Answer unwrapErrorAnswer(final CompletableFuture<?> future) {
        return ((DiameterErrorAnswerException) future.exceptionNow()).getAnswer();
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
    void itCreatesQuintuplets() throws Exception {
        // GIVEN
        final var knownImsi = "999990000263716";
        final var knownUsername = knownImsi + "@ims.mnc003.mcc262.3gppnetwork.org";

        final var mar = buildMar(knownUsername, "Digest-AKAv1-MD5");

        final var randomValue = "random-value1234".getBytes(StandardCharsets.UTF_8);
        when(randomGenerator.nextRand(16)).thenReturn(randomValue);

        final var knownSim = new Sim(null, "some-secret-key-at-least-15-bytes".getBytes(StandardCharsets.UTF_8));
        knownSim.setOp(new byte[16]);
        when(simDao.getSimByImsiStringForUpdate(knownImsi)).thenReturn(Optional.of(knownSim));
        final var knownMsisdn = new Msisdn("known-msisdn");
        knownSim.setMsisdn(knownMsisdn);

        // WHEN
        final var answer = underTest.handle(mar).join();

        // THEN
        assertSuccessfulQuintuplets(answer, knownUsername, knownMsisdn.getMsisdn());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void itLoadsSimForUpdateToSerialiseSqnPerImsi() throws Exception {
        // GIVEN a valid MAR. The MAR path read-increments-writes the SIM's SQN, so the SIM must be
        // loaded under a FOR-UPDATE lock — otherwise two concurrent requests for the same IMSI read
        // the same SQN and produce overlapping sequence numbers.
        final var knownImsi = "999990000263716";
        final var knownUsername = knownImsi + "@ims.mnc003.mcc262.3gppnetwork.org";
        final var mar = buildMar(knownUsername, "Digest-AKAv1-MD5");

        when(randomGenerator.nextRand(16)).thenReturn("random-value1234".getBytes(StandardCharsets.UTF_8));
        final var knownSim = new Sim(null, "some-secret-key-at-least-15-bytes".getBytes(StandardCharsets.UTF_8));
        knownSim.setOp(new byte[16]);
        knownSim.setMsisdn(new Msisdn("known-msisdn"));
        when(simDao.getSimByImsiStringForUpdate(knownImsi)).thenReturn(Optional.of(knownSim));

        // WHEN
        underTest.handle(mar).join();

        // THEN the locking read is used, never the unlocked one.
        verify(simDao).getSimByImsiStringForUpdate(knownImsi);
        verify(simDao, never()).getSimByImsiString(anyString());
    }

    @Test
    void itAnswersWithUnknownSubscriberWhenImsiIsUnknown() throws Exception {
        // GIVEN
        final var unknownImsi = "001001001001001";
        final var unknownUsername = unknownImsi + "@ims.mnc000.mcc000.3gppnetwork.org";
        final var mar = buildMar(unknownUsername, "Digest-AKAv1-MD5");
        when(simDao.getSimByImsiStringForUpdate(anyString())).thenReturn(Optional.empty());

        // WHEN
        final var answer = unwrapErrorAnswer(underTest.handle(mar));

        // THEN
        assertExperimentalErrorAnswer(answer, EXP_RES_DIAMETER_ERROR_USER_UNKNOWN);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void itReturnsErrorWhenAuthenticationErrorsOccur() throws Exception {
        // GIVEN
        final var anyImsi = "001001001001001";
        final var anyUsername = anyImsi + "@ims.mnc000.mcc000.3gppnetwork.org";
        final var mar = buildMar(anyUsername, "Digest-AKAv1-MD5");
        final var invalidSimWhichWillCauseExceptions = new Sim();
        when(simDao.getSimByImsiStringForUpdate(anyString())).thenReturn(Optional.of(invalidSimWhichWillCauseExceptions));

        // WHEN
        final var answer = underTest.handle(mar);

        // THEN
        assertThat(answer).failsWithin(Duration.ZERO);
        verifyNoInteractions(eventPublisher);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "AnyInvalidString"})
    void itValidatesTheSipAuthScheme(final String invalidAuthScheme) throws Exception {
        // GIVEN
        final var anyImsi = "001001001001001";
        final var anyUsername = anyImsi + "@ims.mnc000.mcc000.3gppnetwork.org";
        final var mar = buildMar(anyUsername, invalidAuthScheme);

        // WHEN
        final var answer = unwrapErrorAnswer(underTest.handle(mar));

        // THEN
        assertExperimentalErrorAnswer(answer, EXP_RES_DIAMETER_ERROR_AUTH_SCHEME_NOT_SUPPORTED);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void itReturnsMissingAvpWhenUserNameIsMissing() throws Exception {
        // GIVEN
        final var mar = buildMar(null, "Digest-AKAv1-MD5");

        // WHEN
        final var answer = unwrapErrorAnswer(underTest.handle(mar));

        // THEN
        assertMissingAvp(answer, KEY_USER_NAME);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void itReturnsInvalidAvpValueWhenUserNameIsTooShortForAnImsi() throws Exception {
        // GIVEN
        final var tooShortUsername = "26203";
        final var mar = buildMar(tooShortUsername, "Digest-AKAv1-MD5");

        // WHEN
        final var answer = unwrapErrorAnswer(underTest.handle(mar));

        // THEN: the offending User-Name is echoed inside Failed-AVP
        assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_INVALID_AVP_VALUE);
        final var failedAvp = ((ErrorAnswer) answer).getFailedAVP();
        assertThat(failedAvp).as("Failed-AVP grouped AVP").isNotNull();
        assertThat(failedAvp.findAVP(KEY_USER_NAME).getDataAsString()).isEqualTo(tooShortUsername);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void itReturnsMissingAvpWhenSipAuthDataItemIsMissing() throws Exception {
        // GIVEN
        final var anyImsi = "001001001001001";
        final var anyUsername = anyImsi + "@ims.mnc000.mcc000.3gppnetwork.org";
        final var mar = buildMarWithoutSipAuthDataItem(anyUsername);

        // WHEN
        final var answer = unwrapErrorAnswer(underTest.handle(mar));

        // THEN
        assertMissingAvp(answer, KEY_SIP_AUTH_DATA_ITEM);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void itReturnsMissingAvpWhenSipAuthenticationSchemeIsMissing() throws Exception {
        // GIVEN: the SIP-Auth-Data-Item is present but holds no SIP-Authentication-Scheme
        final var anyImsi = "001001001001001";
        final var anyUsername = anyImsi + "@ims.mnc000.mcc000.3gppnetwork.org";
        final var mar = buildMar(anyUsername, null);

        // WHEN
        final var answer = unwrapErrorAnswer(underTest.handle(mar));

        // THEN
        assertMissingAvp(answer, KEY_SIP_AUTHENTICATION_SCHEME);
        verifyNoInteractions(eventPublisher);
    }

    @Nested
    class RegistrationTerminationTest {
        // see 3GPP TS 29.228 version 16.1.0 Release 16
        // section 8.1.1 Cancellation of the old S-CSCF

        private static final AVPKey KEY_REASON_CODE = new AVPKey(AVP_REASON_CODE, VENDOR_ID_3GPP);
        private static final AVPKey KEY_REASON_INFO = new AVPKey(AVP_REASON_INFO, VENDOR_ID_3GPP);

        @ParameterizedTest
        @MethodSource("com.sipgate.sparta.hss.diameter.cx.mar.MultimediaAuthHandlerTest#provideRtrCase")
        void itReturnsMaaAndSendsRtrWhenSomethingIsDifferent(
            final String newScscf, final String newDiameterHost, final String newDiameterRealm) throws Exception
        {
            // GIVEN
            final var knownImsi = "999990000263716";
            final var knownUsername = knownImsi + "@ims.mnc003.mcc262.3gppnetwork.org";

            final var previousScscf = new ImsiScscf();
            previousScscf.setScscf("previous-scscf");
            previousScscf.setDiameterHost("previous-diameter-host");
            previousScscf.setDiameterRealm("previous-diameter-realm");
            when(imsiScscfDao.getScscf(knownImsi)).thenReturn(Optional.of(previousScscf));

            final var mar = buildMar(knownUsername, "Digest-AKAv1-MD5", newScscf, newDiameterHost, newDiameterRealm);

            final var randomValue = "random-value1234".getBytes(StandardCharsets.UTF_8);
            when(randomGenerator.nextRand(16)).thenReturn(randomValue);

            final var knownSim = new Sim(null, "some-secret-key-at-least-15-bytes".getBytes(StandardCharsets.UTF_8));
            knownSim.setOp(new byte[16]);
            when(simDao.getSimByImsiStringForUpdate(knownImsi)).thenReturn(Optional.of(knownSim));
            final var knownMsisdn = new Msisdn("known-msisdn");
            knownSim.setMsisdn(knownMsisdn);

            // WHEN
            final var answer = underTest.handle(mar).join();

            // THEN
            assertSuccessfulQuintuplets(answer, knownUsername, knownMsisdn.getMsisdn());


            final var eventCaptor = ArgumentCaptor.forClass(RegistrationTerminationEvent.class);
            verify(eventPublisher).publish(eventCaptor.capture());

            // the Session-Id is stamped by the outbound listener, not the handler, so it is unset here
            final var rtr = (RegistrationTerminationRequest.Out) eventCaptor.getValue().getRequest();
            assertThat(rtr.getAuthSessionState()).isEqualTo(AUTH_SESSION_STATE_NOT_MAINTAINED);
            assertThat(rtr.getUserName()).isEqualTo(knownUsername);
            assertThat(rtr.getPublicIdentities()).containsExactly("tel:+" + knownMsisdn.getMsisdn());
            assertThat(rtr.getDestinationHost()).isEqualTo("previous-diameter-host");
            assertThat(rtr.getDestinationRealm()).isEqualTo("previous-diameter-realm");
            assertThat(rtr.getApplicationId()).isEqualTo(CxDxConstants.APP_ID_CX_DX);
            assertThat(rtr.getVendorSpecificApplicationId().findAVP(KEY_AUTH_APPLICATION_ID).getDataAsUnsignedInt())
                .isEqualTo(CxDxConstants.APP_ID_CX_DX);

            final var deregistrationReason = rtr.getDeregistrationReason();
            assertThat(deregistrationReason).as("Deregistration-Reason grouped AVP").isNotNull();
            assertThat(deregistrationReason.findAVP(KEY_REASON_CODE).getDataAsInt())
                .isEqualTo(REASON_CODE_NEW_SERVER_ASSIGNED);
            assertThat(deregistrationReason.findAVP(KEY_REASON_INFO).getDataAsString())
                .isEqualTo("UE de-registration due to new s-cscf assignment");

            assertThat(rtr.getAssociatedIdentities()).as("Associated-Identities grouped AVP").isNull();

            verify(imsiScscfDao).clearScscf(knownImsi);
        }

        @Test
        void itReturnsMaaAndDoesNotSendRtrWhenEverythingIsTheSame() throws Exception {
            // GIVEN
            final var knownImsi = "999990000263716";
            final var knownUsername = knownImsi + "@ims.mnc003.mcc262.3gppnetwork.org";

            final var previousScscf = new ImsiScscf();
            previousScscf.setScscf("previous-scscf");
            previousScscf.setDiameterHost("previous-diameter-host");
            previousScscf.setDiameterRealm("previous-diameter-realm");
            when(imsiScscfDao.getScscf(knownImsi)).thenReturn(Optional.of(previousScscf));

            final var mar = buildMar(
                knownUsername, "Digest-AKAv1-MD5",
                previousScscf.getScscf(), previousScscf.getDiameterHost(), previousScscf.getDiameterRealm());

            final var randomValue = "random-value1234".getBytes(StandardCharsets.UTF_8);
            when(randomGenerator.nextRand(16)).thenReturn(randomValue);

            final var knownSim = new Sim(null, "some-secret-key-at-least-15-bytes".getBytes(StandardCharsets.UTF_8));
            knownSim.setOp(new byte[16]);
            when(simDao.getSimByImsiStringForUpdate(knownImsi)).thenReturn(Optional.of(knownSim));
            final var knownMsisdn = new Msisdn("known-msisdn");
            knownSim.setMsisdn(knownMsisdn);

            // WHEN
            final var answer = underTest.handle(mar).join();

            // THEN
            assertSuccessfulQuintuplets(answer, knownUsername, knownMsisdn.getMsisdn());
            verifyNoInteractions(eventPublisher);
            verify(imsiScscfDao, never()).clearScscf(anyString());
        }

        @Test
        void itClearsScscfAndSendsRtrBeforeGenerateFailure() throws Exception {
            // GIVEN — a previous S-CSCF that differs from the request, triggering RTR
            final var knownImsi = "999990000263716";
            final var knownUsername = knownImsi + "@ims.mnc003.mcc262.3gppnetwork.org";

            final var previousScscf = new ImsiScscf();
            previousScscf.setScscf("previous-scscf");
            previousScscf.setDiameterHost("previous-diameter-host");
            previousScscf.setDiameterRealm("previous-diameter-realm");
            when(imsiScscfDao.getScscf(knownImsi)).thenReturn(Optional.of(previousScscf));

            // corrupt resync: 16-byte RAND + 14-byte AUTS (30 bytes) — valid length, AUTS MAC won't match
            final var corruptResync = new byte[30];
            final var mar = buildMar(
                knownUsername, "Digest-AKAv1-MD5",
                "other-scscf", "other-diameter-host", "other-diameter-realm",
                corruptResync);

            final var knownSim = new Sim(null, "some-secret-key-at-least-15-bytes".getBytes(StandardCharsets.UTF_8));
            knownSim.setOp(new byte[16]);
            knownSim.setMsisdn(new Msisdn("known-msisdn"));
            when(simDao.getSimByImsiStringForUpdate(knownImsi)).thenReturn(Optional.of(knownSim));

            // WHEN — generate throws (resync MAC mismatch), caught by handler
            final var answer = underTest.handle(mar);

            // THEN
            // (a) clearScscf + (b) RegistrationTerminationEvent published, in order, before generate failure
            final var inOrder = inOrder(imsiScscfDao, eventPublisher);
            inOrder.verify(imsiScscfDao).clearScscf(knownImsi);
            inOrder.verify(eventPublisher).publish(any(RegistrationTerminationEvent.class));

            // the answer reflects the generate failure
            assertExperimentalErrorAnswer(unwrapErrorAnswer(answer), CxDxConstants.EXP_RES_DIAMETER_ERROR_SERVING_NODE_FEATURE_UNSUPPORTED);

            // (c) sim.sqn advanced — forward-progress preserved despite resync failure
            assertThat(knownSim.getSqn()).isEqualTo(34L);
        }
    }
}
