package com.sipgate.sparta.hss.diameter.s6a.air;

import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.EXP_RES_DIAMETER_ERROR_USER_UNKNOWN;
import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.VENDOR_ID_3GPP;
import static com.sipgate.sparta.diameter._3gpp.s6a.S6aConstants.AVP_AUTN;
import static com.sipgate.sparta.diameter._3gpp.s6a.S6aConstants.AVP_E_UTRAN_VECTOR;
import static com.sipgate.sparta.diameter._3gpp.s6a.S6aConstants.AVP_ITEM_NUMBER;
import static com.sipgate.sparta.diameter._3gpp.s6a.S6aConstants.AVP_KASME;
import static com.sipgate.sparta.diameter._3gpp.s6a.S6aConstants.AVP_NUMBER_OF_REQUESTED_VECTORS;
import static com.sipgate.sparta.diameter._3gpp.s6a.S6aConstants.AVP_RAND;
import static com.sipgate.sparta.diameter._3gpp.s6a.S6aConstants.AVP_RE_SYNCHRONIZATION_INFO;
import static com.sipgate.sparta.diameter._3gpp.s6a.S6aConstants.AVP_UTRAN_VECTOR;
import static com.sipgate.sparta.diameter._3gpp.s6a.S6aConstants.AVP_XRES;
import static com.sipgate.sparta.diameter._3gpp.s6a.S6aConstants.EXP_RES_DIAMETER_AUTHENTICATION_DATA_UNAVAILABLE;
import static com.sipgate.sparta.diameter._3gpp.s6a.S6aConstants.EXP_RES_DIAMETER_ERROR_UNKNOWN_EPS_SUBSCRIPTION;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.AVP_EXPERIMENTAL_RESULT_CODE;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.AVP_VENDOR_ID;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.RES_DIAMETER_MISSING_AVP;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.RES_DIAMETER_SUCCESS;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.RES_DIAMETER_UNABLE_TO_COMPLY;
import static com.sipgate.sparta.hss.diameter.s6a.air.AuthenticationInfoHandler.MAX_NUMBER_OF_VECTORS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants;
import com.sipgate.sparta.diameter._3gpp.s6a.S6aConstants;
import com.sipgate.sparta.diameter._3gpp.s6a.messages.AuthenticationInformationAnswer;
import com.sipgate.sparta.diameter._3gpp.s6a.messages.AuthenticationInformationRequest;
import com.sipgate.sparta.diameter.base.core.Answer;
import com.sipgate.sparta.diameter.base.core.Command;
import com.sipgate.sparta.diameter.base.core.DiameterConstants;
import com.sipgate.sparta.diameter.base.core.EndToEndId;
import com.sipgate.sparta.diameter.base.core.HopByHopId;
import com.sipgate.sparta.diameter.base.core.avp.AVP;
import com.sipgate.sparta.diameter.base.core.avp.AVPContainer;
import com.sipgate.sparta.diameter.base.core.avp.AVPKey;
import com.sipgate.sparta.diameter.base.session.DiameterErrorAnswerException;
import com.sipgate.sparta.hss.diameter.common.Factory;
import com.sipgate.sparta.hss.diameter.common.auth.MilenageAuthenticator;
import com.sipgate.sparta.hss.diameter.common.auth.StoredKeyFormat;
import com.sipgate.sparta.hss.diameter.common.auth.RandomGenerator;
import com.sipgate.sparta.hss.persistence.SimDao;
import com.sipgate.sparta.hss.persistence.entities.Sim;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.xml.bind.DatatypeConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Test tries to replicate the structure of the TS 129.272 section 5.2.3.1.3. In that section, there are a lot
 * of IF-Cases that we have adapted to be a nested test structure so that the output of the test reads more or
 * less like the specification.
 *
 * <p>There are some exceptions to this rule, but generally the test structure should be similar to the specification.
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationInfoHandlerTest {

    // Immediate-Response-Preferred (3GPP TS 29.272 §7.3.16) is not part of the lib dictionary
    // (the handler never reads it), so the test attaches it as a raw, non-mandatory AVP.
    private static final int AVP_IMMEDIATE_RESPONSE_PREFERRED = 1412;

    private static final AVPKey KEY_NUMBER_OF_REQUESTED_VECTORS = new AVPKey(AVP_NUMBER_OF_REQUESTED_VECTORS, VENDOR_ID_3GPP);
    private static final AVPKey KEY_RE_SYNCHRONIZATION_INFO = new AVPKey(AVP_RE_SYNCHRONIZATION_INFO, VENDOR_ID_3GPP);
    private static final AVPKey KEY_E_UTRAN_VECTOR = new AVPKey(AVP_E_UTRAN_VECTOR, VENDOR_ID_3GPP);
    private static final AVPKey KEY_UTRAN_VECTOR = new AVPKey(AVP_UTRAN_VECTOR, VENDOR_ID_3GPP);
    private static final AVPKey KEY_ITEM_NUMBER = new AVPKey(AVP_ITEM_NUMBER, VENDOR_ID_3GPP);
    private static final AVPKey KEY_RAND = new AVPKey(AVP_RAND, VENDOR_ID_3GPP);
    private static final AVPKey KEY_XRES = new AVPKey(AVP_XRES, VENDOR_ID_3GPP);
    private static final AVPKey KEY_AUTN = new AVPKey(AVP_AUTN, VENDOR_ID_3GPP);
    private static final AVPKey KEY_KASME = new AVPKey(AVP_KASME, VENDOR_ID_3GPP);
    private static final AVPKey KEY_CONFIDENTIALITY_KEY = new AVPKey(CxDxConstants.AVP_CONFIDENTIALITY_KEY, VENDOR_ID_3GPP);
    private static final AVPKey KEY_INTEGRITY_KEY = new AVPKey(CxDxConstants.AVP_INTEGRITY_KEY, VENDOR_ID_3GPP);
    private static final AVPKey KEY_VENDOR_ID = new AVPKey(AVP_VENDOR_ID, 0);
    private static final AVPKey KEY_EXPERIMENTAL_RESULT_CODE = new AVPKey(AVP_EXPERIMENTAL_RESULT_CODE, 0);

    private static final String IMSI = "999990000104857";
    /** Visited-PLMN-Id "26207" (MCC=262, MNC=07) in the 3-octet wire encoding of TS 23.003 §12.1. */
    private static final byte[] VISITED_PLMN_26207 = {0x62, (byte) 0xF2, 0x70};

    private static final String FIXED_RAND = "4EC67BF2956DE16702D68E3AF481CA2C";

    private static final String EXPECTED_XRES = "CE24257EEED33B5A";
    private static final String EXPECTED_EUTRAN_AUTN_SQN_310 = "263B1975FF74800015A0616A9C3AAFDE";
    private static final String EXPECTED_KASME_SQN_310 = "8891B65582D3D46DCBE5256105DDDDA0FD83F96E02E800E417A64E0B69FCB593";
    private static final String EXPECTED_UTRAN_AUTN_SQN_310 = "263B1975FF7400004A6CDC4DBCBEF815";
    private static final String EXPECTED_UTRAN_AUTN_SQN_312 = "263B1975FF7A000070253459AA4DEE73";
    private static final String EXPECTED_CK = "E1B2BDC2B86EA755FDDE522D5B5225FD";
    private static final String EXPECTED_IK = "A55BCE70977E953E50DFACA5358C8A76";
    private static final long RES_DIAMETER_INVALID_AVP_VALUE = 5004L;

    // Valid Re-Synchronization-Info (RAND_MS || AUTS) for the Factory.buildSim() K/OP, claiming SQN_MS=5000:
    // AUTS = (SQN_MS xor f5*(K, RAND_MS)) || f1*(K, RAND_MS, SQN_MS, AMF=0000), computed with the same
    // (unchanged and separately tested) Milenage implementation as the constants above. A successful resync
    // sets the SIM SQN to SQN_MS + 10 (resync safety margin) = 5010 — far away from the default-path 310,
    // so a vector matching the SQN-5010 expectations below proves the handler fed the AVP into the resync.
    private static final String RESYNCHRONIZATION_INFO = "0123456789ABCDEF0123456789ABCDEF7FD149E1C0A5BB7EBED82B1154C7";
    private static final String EXPECTED_EUTRAN_AUTN_SQN_5010 = "263B1975EDD0800010127FF47F02F8DF";
    private static final String EXPECTED_KASME_SQN_5010 = "CC3DEB8548A7C9446D164D7E7E38AD0CD70A1E471E65EB62B2DB97DA5B90578D";

    private AuthenticationInfoHandler underTest;

    @Mock
    private SimDao simDao;

    @Mock
    private EutranAccessPolicy eutranAccessPolicy;

    @Mock
    private RandomGenerator randGen;

    @BeforeEach
    void setUp() {
        final var authenticator = new MilenageAuthenticator(randGen, new SimpleMeterRegistry(), StoredKeyFormat.OP);
        underTest = new AuthenticationInfoHandler(simDao, eutranAccessPolicy, authenticator);
    }

    /**
     * Builds an incoming Authentication-Information-Request with the given Requested-EUTRAN- /
     * Requested-UTRAN-GERAN-Authentication-Info sub-AVPs ({@code null} omits the grouped AVP entirely).
     * A wire-parsed {@code .In} command is immutable, so the request is assembled as an outgoing
     * message, serialized and parsed back — exactly how the stack produces inbound requests at runtime.
     */
    private static AuthenticationInformationRequest.In buildRequest(
            final List<AVP> requestedEutranAuthInfo,
            final List<AVP> requestedUtranGeranAuthInfo)
        throws Exception
    {
        final var out = new AuthenticationInformationRequest.Out();
        out.setOriginHost("mmec02.mmegi8001.mme.epc.mnc007.mcc262.3gppnetwork.org");
        out.setOriginRealm("epc.mnc007.mcc262.3gppnetwork.org");
        // as every MME sends them; the answer has to carry them back
        out.setAuthSessionState(DiameterConstants.AUTH_SESSION_STATE_NOT_MAINTAINED);
        out.setVendorSpecificApplicationId(List.of(
            AVP.create(new AVPKey(DiameterConstants.AVP_VENDOR_ID, 0), (long) VENDOR_ID_3GPP),
            AVP.create(new AVPKey(DiameterConstants.AVP_AUTH_APPLICATION_ID, 0), (long) S6aConstants.APP_ID_S6A_S6D)));
        out.setUserName(IMSI);
        out.setVisitedPlmnId(VISITED_PLMN_26207);
        if (requestedEutranAuthInfo != null) {
            out.setRequestedEutranAuthenticationInfo(requestedEutranAuthInfo);
        }
        if (requestedUtranGeranAuthInfo != null) {
            out.setRequestedUtranGeranAuthenticationInfo(requestedUtranGeranAuthInfo);
        }

        final var baos = new ByteArrayOutputStream();
        out.writeTo(new DataOutputStream(baos), new HopByHopId(1), new EndToEndId(2));
        return (AuthenticationInformationRequest.In) Command.parseMessage(ByteBuffer.wrap(baos.toByteArray()));
    }

    /** Requested-*-Authentication-Info content as in the legacy fixtures: vector count + Immediate-Response-Preferred. */
    private static List<AVP> requestedAuthInfo(final long numberOfRequestedVectors) {
        final var avps = new ArrayList<AVP>();
        avps.add(AVP.create(KEY_NUMBER_OF_REQUESTED_VECTORS, numberOfRequestedVectors));
        avps.add(immediateResponsePreferred(1));
        return avps;
    }

    private static List<AVP> requestedAuthInfoWithResync(final long numberOfRequestedVectors) {
        final var avps = requestedAuthInfo(numberOfRequestedVectors);
        avps.add(AVP.create(KEY_RE_SYNCHRONIZATION_INFO, DatatypeConverter.parseHexBinary(RESYNCHRONIZATION_INFO)));
        return avps;
    }

    private static AVP immediateResponsePreferred(final int value) {
        return AVP.createRaw(
            new AVPKey(AVP_IMMEDIATE_RESPONSE_PREFERRED, VENDOR_ID_3GPP),
            true, false, false,
            new byte[] {0, 0, 0, (byte) value});
    }

    private void subscriberHasNotAnyApnConfiguration() {
        when(eutranAccessPolicy.isEutranAccessAllowed(anyString(), anyString())).thenReturn(false);
    }

    private void subscriberHasApnConfiguration() {
        when(eutranAccessPolicy.isEutranAccessAllowed(anyString(), anyString())).thenReturn(true);
    }

    private void randomGeneratorReturnsFixedRand() {
        when(randGen.nextRand(anyInt())).thenReturn(DatatypeConverter.parseHexBinary(FIXED_RAND));
    }

    private static void assertIsSuccess(final AuthenticationInformationAnswer.Out answer) {
        assertThat(answer.findAVP(new AVPKey(DiameterConstants.AVP_EXPERIMENTAL_RESULT, 0))).isNull();
        assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_SUCCESS);
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
        // an E-bit answer bypasses the S6a message factory, so these two have to be mirrored
        // from the request - without them the MME sees a truncated AIA
        assertThat(answer.findAVP(new AVPKey(DiameterConstants.AVP_AUTH_SESSION_STATE, 0)).getDataAsInt())
            .as("Auth-Session-State").isEqualTo(DiameterConstants.AUTH_SESSION_STATE_NOT_MAINTAINED);
        final var vsai = (AVPContainer) answer.findAVP(new AVPKey(DiameterConstants.AVP_VENDOR_SPECIFIC_APPLICATION_ID, 0));
        assertThat(vsai).as("Vendor-Specific-Application-Id").isNotNull();
        assertThat(vsai.findAVP(new AVPKey(DiameterConstants.AVP_AUTH_APPLICATION_ID, 0)).getDataAsUnsignedInt())
            .isEqualTo(S6aConstants.APP_ID_S6A_S6D);
    }

    private static List<AVPContainer> vectorsOf(final AuthenticationInformationAnswer.Out answer, final AVPKey vectorKey) {
        final var authenticationInfo = answer.getAuthenticationInfo();
        assertThat(authenticationInfo).as("Authentication-Info grouped AVP").isNotNull();
        return authenticationInfo.findAVPs(vectorKey).stream()
            .map(avp -> (AVPContainer) avp)
            .toList();
    }

    private static String hex(final AVPContainer vector, final AVPKey key) {
        return DatatypeConverter.printHexBinary(vector.findAVP(key).getDataAsOctetString());
    }

    private static Answer unwrapErrorAnswer(final CompletableFuture<?> future) {
        return ((DiameterErrorAnswerException) future.exceptionNow()).getAnswer();
    }

    @Test
    void testMinSqn() throws Exception {
        final var imsi = Factory.buildImsi();
        final var imsiSet = Factory.buildImsiSet(imsi);
        final var sim = Factory.buildSim(imsiSet);
        sim.setSqn(1L);

        when(simDao.getSimByImsiString(anyString())).thenReturn(Optional.of(sim));
        subscriberHasApnConfiguration();
        randomGeneratorReturnsFixedRand();

        final var request = requestedAuthInfo(1L);

        underTest.handle(buildRequest(request, null)).join();

        assertThat(sim.getSqn()).isEqualTo(34L);
    }

    @Nested
    class OnError {
        @Test
        void itReturnsAuthDataUnavailable() throws Exception {
            // GIVEN no corresponding pre-computed AV is available, and the AuC is unable to calculate any
            // corresponding AVs due to unknown failures, such as the internal database error
            final var exception = new RuntimeException("any exception");
            when(simDao.getSimByImsiString(anyString())).thenThrow(exception);

            final var request = requestedAuthInfo(1L);

            // WHEN
            final var actual = underTest.handle(buildRequest(request, null)).exceptionNow();

            // THEN we forward any exception and the diameter lib will create and send the correct error answer
            assertThat(actual).isSameAs(exception);
        }
    }

    @Nested
    class WithoutAnyTypeOfSubscription {
        @Test
        void itReturnsUserUnknown() throws Exception {
            // GIVEN there is not any type of subscription for the IMSI (including EPS, GPRS and CS subscription data)
            when(simDao.getSimByImsiString(anyString())).thenReturn(Optional.empty());

            final var request = requestedAuthInfo(1L);

            // WHEN
            final var answer = unwrapErrorAnswer(underTest.handle(buildRequest(request, null)));

            // THEN a result code of DIAMETER_ERROR_USER_UNKNOWN shall be returned.
            assertExperimentalErrorAnswer(answer, EXP_RES_DIAMETER_ERROR_USER_UNKNOWN);
        }
    }

    @Nested
    class ImsiIsKnown {
        private Sim sim;

        @BeforeEach
        void setUp() {
            // GIVEN IMSI is known
            final var imsi = Factory.buildImsi();
            final var imsiSet = Factory.buildImsiSet(imsi);
            sim = Factory.buildSim(imsiSet);
            when(simDao.getSimByImsiString(anyString())).thenReturn(Optional.of(sim));
        }

        @Nested
        class RequestsEutranOnly {
            // GIVEN the Authentication Information Request contains a Requested-EUTRAN-Authentication-Info AVP
            // but no Requested-UTRAN-GERAN-Authentication-Info AVP

            @Nested
            class WithoutApnConfiguration {
                @Test
                void itReturnsUnknownEpsSubscription() throws Exception {
                    // GIVEN the subscriber has not any APN configuration
                    subscriberHasNotAnyApnConfiguration();

                    final var request = buildRequest(requestedAuthInfo(1L), null);

                    // WHEN
                    final var answer = unwrapErrorAnswer(underTest.handle(request));

                    // THEN the HSS shall return a Result Code of DIAMETER_ERROR_UNKNOWN_EPS_SUBSCRIPTION.
                    assertExperimentalErrorAnswer(answer, EXP_RES_DIAMETER_ERROR_UNKNOWN_EPS_SUBSCRIPTION);
                }
            }

            @Nested
            class WithApnConfiguration {
                @BeforeEach
                void setUp() {
                    subscriberHasApnConfiguration();
                    randomGeneratorReturnsFixedRand();
                }

                @Test
                void itSendsEutranAuthenticationVectors() throws Exception {
                    // GIVEN
                    final var request = buildRequest(requestedAuthInfo(1L), null);

                    // WHEN
                    final var answer = underTest.handle(request).join();

                    // THEN the HSS shall download E-UTRAN authentication vectors to the MME
                    assertIsSuccess(answer);

                    final var eutranVectors = vectorsOf(answer, KEY_E_UTRAN_VECTOR);
                    assertThat(eutranVectors).hasSize(1);
                    final var vector = eutranVectors.getFirst();
                    assertThat(hex(vector, KEY_RAND)).isEqualTo(FIXED_RAND);
                    assertThat(hex(vector, KEY_XRES)).isEqualTo(EXPECTED_XRES);
                    assertThat(hex(vector, KEY_AUTN)).isEqualTo(EXPECTED_EUTRAN_AUTN_SQN_310);
                    assertThat(hex(vector, KEY_KASME)).isEqualTo(EXPECTED_KASME_SQN_310);
                    assertThat(sim.getSqn()).isEqualTo(310L);

                    assertThat(vectorsOf(answer, KEY_UTRAN_VECTOR)).isEmpty();

                    // THEN the ItemNumber AVP shall be present within each Vector.
                    assertThat(vector.findAVP(KEY_ITEM_NUMBER).getDataAsUnsignedInt()).isEqualTo(1L);
                }

                @Nested
                class WithTooManyVectorsRequested {
                    @Test
                    void itSendsMaxNumberOfVectors() throws Exception {
                        // GIVEN more vectors requested than the maximum number of vectors
                        final var request = buildRequest(requestedAuthInfo(MAX_NUMBER_OF_VECTORS + 1L), null);

                        // WHEN
                        final var answer = underTest.handle(request).join();

                        // THEN the HSS shall download E-UTRAN authentication vectors to the MME
                        assertIsSuccess(answer);
                        final var vectors = vectorsOf(answer, KEY_E_UTRAN_VECTOR);
                        assertThat(vectors).hasSize(MAX_NUMBER_OF_VECTORS);

                        assertThat(vectorsOf(answer, KEY_UTRAN_VECTOR)).isEmpty();

                        // THEN the ItemNumber AVP shall be present within each Vector.
                        for (var i = 0; i < MAX_NUMBER_OF_VECTORS; i++) {
                            assertThat(vectors.get(i).findAVP(KEY_ITEM_NUMBER).getDataAsUnsignedInt())
                                    .isEqualTo(i + 1L);
                        }
                    }
                }

                @Nested
                class WithGenerationFailure {
                    @Test
                    void itReturnsAuthDataUnavailableWithoutVectorsEutran() throws Exception {
                        // GIVEN vector generation fails before any vector can be computed
                        when(randGen.nextRand(anyInt())).thenThrow(new RuntimeException("generation failed"));
                        final var request = buildRequest(requestedAuthInfo(1L), null);

                        // WHEN
                        final var answer = unwrapErrorAnswer(underTest.handle(request));

                        // THEN
                        assertExperimentalErrorAnswer(answer, EXP_RES_DIAMETER_AUTHENTICATION_DATA_UNAVAILABLE);
                    }
                    @Test
                    void itReturnsAuthDataUnavailableWithoutVectorsUtran() throws Exception {
                        // GIVEN vector generation fails before any vector can be computed
                        when(randGen.nextRand(anyInt())).thenThrow(new RuntimeException("generation failed"));
                        final var request = buildRequest(null, requestedAuthInfo(1L));

                        // WHEN
                        final var answer = unwrapErrorAnswer(underTest.handle(request));

                        // THEN
                        assertExperimentalErrorAnswer(answer, EXP_RES_DIAMETER_AUTHENTICATION_DATA_UNAVAILABLE);
                    }
                }

                @Nested
                class WithResynchronizationInfo {
                    @Test
                    void itSendsVectorsBasedOnTheResynchronizedSqn() throws Exception {
                        // GIVEN the Requested-EUTRAN-Authentication-Info AVP includes a Re-Synchronization-Info
                        // AVP (SQN_MS = 5000, see RESYNCHRONIZATION_INFO)
                        final var request = buildRequest(requestedAuthInfoWithResync(1L), null);

                        // WHEN
                        final var answer = underTest.handle(request).join();

                        // THEN the HSS shall perform resynchronization and download E-UTRAN vectors that are
                        // derived from the resynchronized SQN (5010), not from the default-incremented SQN (310)
                        assertIsSuccess(answer);

                        final var eutranVectors = vectorsOf(answer, KEY_E_UTRAN_VECTOR);
                        assertThat(eutranVectors).hasSize(1);
                        final var vector = eutranVectors.getFirst();
                        assertThat(hex(vector, KEY_RAND)).isEqualTo(FIXED_RAND);
                        assertThat(hex(vector, KEY_XRES)).isEqualTo(EXPECTED_XRES);
                        assertThat(hex(vector, KEY_AUTN)).isEqualTo(EXPECTED_EUTRAN_AUTN_SQN_5010);
                        assertThat(hex(vector, KEY_KASME)).isEqualTo(EXPECTED_KASME_SQN_5010);
                        assertThat(sim.getSqn()).isEqualTo(5010L);

                        assertThat(vectorsOf(answer, KEY_UTRAN_VECTOR)).isEmpty();
                    }
                }
            }

            @Nested
            class WithInvalidNumberOfRequestedVectors {
                @BeforeEach
                void setUp() {
                    subscriberHasApnConfiguration();
                }

                @Test
                void itReturnsInvalidAvpValueWithoutVectorsEutran() throws Exception {
                    // GIVEN
                    final var request = buildRequest(requestedAuthInfo(0L), null);

                    // WHEN
                    final var answer = unwrapErrorAnswer(underTest.handle(request));

                    // THEN
                    assertThat(answer.findAVP(new AVPKey(DiameterConstants.AVP_EXPERIMENTAL_RESULT, 0))).isNull();
                    assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_INVALID_AVP_VALUE);
                }
                @Test
                void itReturnsInvalidAvpValueWithoutVectorsUtran() throws Exception {
                    // GIVEN
                    final var request = buildRequest(null, requestedAuthInfo(0L));

                    // WHEN
                    final var answer = unwrapErrorAnswer(underTest.handle(request));

                    // THEN
                    assertThat(answer.findAVP(new AVPKey(DiameterConstants.AVP_EXPERIMENTAL_RESULT, 0))).isNull();
                    assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_INVALID_AVP_VALUE);
                }

                @Test
                void itReturnsAuthDataUnavailableWithoutEutranAvp() throws Exception {
                    // GIVEN the Requested-EUTRAN-Authentication-Info AVP does not contain a
                    // Number-Of-Requested-Vectors AVP.
                    final var request = buildRequest(List.of(immediateResponsePreferred(1)), null);

                    // WHEN
                    final var answer = unwrapErrorAnswer(underTest.handle(request));

                    // THEN
                    assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_MISSING_AVP);
                }

                @Test
                void itReturnsAuthDataUnavailableWithoutUtranAvp() throws Exception {
                    // GIVEN the Requested-EUTRAN-Authentication-Info AVP does not contain a
                    // Number-Of-Requested-Vectors AVP.
                    final var request = buildRequest(null, List.of(immediateResponsePreferred(1)));

                    // WHEN
                    final var answer = unwrapErrorAnswer(underTest.handle(request));

                    // THEN
                    assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_MISSING_AVP);
                }
            }
        }

        @Nested
        class RequestsUtranGeranOnly {
            // GIVEN the Authentication Information Request contains a Requested-UTRAN-GERAN-Authentication-Info
            // AVP but no Requested-EUTRAN-Authentication-Info AVP

            @Nested
            class WithoutApnConfigurationWithoutGprsSubscriptionData {
                @Test
                void itReturnsUnknownEpsSubscription() throws Exception {
                    // GIVEN the subscriber has neither an APN configuration profile nor GPRS subscription data
                    subscriberHasNotAnyApnConfiguration();

                    final var request = buildRequest(null, requestedAuthInfo(1L));

                    // WHEN
                    final var answer = unwrapErrorAnswer(underTest.handle(request));

                    // THEN the HSS shall return a Result Code of DIAMETER_ERROR_UNKNOWN_EPS_SUBSCRIPTION.
                    assertExperimentalErrorAnswer(answer, EXP_RES_DIAMETER_ERROR_UNKNOWN_EPS_SUBSCRIPTION);
                }
            }

            @Nested
            class WithApnConfiguration {
                @BeforeEach
                void setUp() {
                    // GIVEN
                    subscriberHasApnConfiguration();
                    randomGeneratorReturnsFixedRand();
                }

                @Test
                void itSendsUtranOrGeranAuthenticationVectors() throws Exception {
                    final var request = buildRequest(null, requestedAuthInfo(1L));

                    // WHEN
                    final var answer = underTest.handle(request).join();

                    // THEN the HSS shall download UTRAN or GERAN authentication vectors to the SGSN
                    assertIsSuccess(answer);
                    final var utranVectors = vectorsOf(answer, KEY_UTRAN_VECTOR);
                    assertThat(utranVectors).hasSize(1);
                    final var vector = utranVectors.getFirst();
                    assertThat(hex(vector, KEY_RAND)).isEqualTo(FIXED_RAND);
                    assertThat(hex(vector, KEY_XRES)).isEqualTo(EXPECTED_XRES);
                    assertThat(hex(vector, KEY_AUTN)).isEqualTo(EXPECTED_UTRAN_AUTN_SQN_310);
                    assertThat(hex(vector, KEY_CONFIDENTIALITY_KEY)).isEqualTo(EXPECTED_CK);
                    assertThat(hex(vector, KEY_INTEGRITY_KEY)).isEqualTo(EXPECTED_IK);
                    assertThat(sim.getSqn()).isEqualTo(310L);

                    assertThat(vectorsOf(answer, KEY_E_UTRAN_VECTOR)).isEmpty();

                    // THEN the ItemNumber AVP shall be present within each Vector.
                    assertThat(vector.findAVP(KEY_ITEM_NUMBER).getDataAsUnsignedInt()).isEqualTo(1L);
                }

                @Nested
                class WithTooManyVectorsRequested {
                    @Test
                    void itSendsMaxNumberOfVectors() throws Exception {
                        // GIVEN more vectors requested than the maximum number of vectors
                        final var request = buildRequest(null, requestedAuthInfo(MAX_NUMBER_OF_VECTORS + 1L));

                        // WHEN
                        final var answer = underTest.handle(request).join();

                        // THEN the HSS shall download UTRAN or GERAN authentication vectors to the SGSN
                        assertIsSuccess(answer);

                        final var vectors = vectorsOf(answer, KEY_UTRAN_VECTOR);
                        assertThat(vectors).hasSize(MAX_NUMBER_OF_VECTORS);
                        assertThat(vectorsOf(answer, KEY_E_UTRAN_VECTOR)).isEmpty();

                        // THEN the ItemNumber AVP shall be present within each Vector.
                        for (var i = 0; i < MAX_NUMBER_OF_VECTORS; i++) {
                            assertThat(vectors.get(i).findAVP(KEY_ITEM_NUMBER).getDataAsUnsignedInt())
                                    .isEqualTo(i + 1L);
                        }
                    }
                }
            }
        }

        @Nested
        class RequestsEutranAndUtranGeran {
            // GIVEN the Authentication Information Request contains both Requested-EUTRAN-Authentication-Info AVP
            // and Requested-UTRAN-GERAN-Authentication-Info AVP

            @Nested
            class EutranDoesNotContainImmediateResponsePreferred {
                @Nested
                class WithoutApnConfiguration {
                    @Test
                    void itDoesNotSendEutranVectors() throws Exception {
                        // GIVEN the Requested-EUTRAN-Authentication-Info AVP does not contain an
                        // Immediate-Response-Preferred AVP
                        final var request = buildRequest(
                            List.of(AVP.create(KEY_NUMBER_OF_REQUESTED_VECTORS, 1L)),
                            requestedAuthInfo(1L));

                        // GIVEN the subscriber has not any APN configuration,
                        subscriberHasNotAnyApnConfiguration();

                        // WHEN
                        final var answer = unwrapErrorAnswer(underTest.handle(request));

                        // THEN the HSS shall not return E-UTRAN vectors.
                        assertExperimentalErrorAnswer(answer, EXP_RES_DIAMETER_ERROR_UNKNOWN_EPS_SUBSCRIPTION);
                    }
                }
            }

            @Nested
            class EutranContainsImmediateResponsePreferred {
                @Nested
                class WithoutApnConfiguration {
                    @Test
                    void itReturnsUnknownEpsSubscription() throws Exception {
                        // GIVEN the Requested-EUTRAN-Authentication-Info AVP contains an
                        // Immediate-Response-Preferred AVP
                        final var request = buildRequest(
                            List.of(AVP.create(KEY_NUMBER_OF_REQUESTED_VECTORS, 1L), immediateResponsePreferred(42)),
                            requestedAuthInfo(1L));

                        // GIVEN the subscriber does not have any APN configuration
                        subscriberHasNotAnyApnConfiguration();

                        // WHEN
                        final var answer = unwrapErrorAnswer(underTest.handle(request));

                        // THEN the HSS shall return a Result Code of DIAMETER_ERROR_UNKNOWN_EPS_SUBSCRIPTION.
                        assertExperimentalErrorAnswer(answer, EXP_RES_DIAMETER_ERROR_UNKNOWN_EPS_SUBSCRIPTION);
                    }
                }
            }

            @Nested
            class WithApnConfiguration {
                @BeforeEach
                void setUp() {
                    // GIVEN
                    subscriberHasApnConfiguration();
                    randomGeneratorReturnsFixedRand();
                }

                @Test
                void itSendsEutranAndUtranOrGeranAuthenticationVectors() throws Exception {
                    final var request = buildRequest(requestedAuthInfo(1L), requestedAuthInfo(1L));

                    // WHEN
                    final var answer = underTest.handle(request).join();

                    // THEN
                    assertIsSuccess(answer);

                    // THEN the HSS shall download E-UTRAN authentication vectors to the MME
                    final var eutranVector = vectorsOf(answer, KEY_E_UTRAN_VECTOR).getFirst();
                    assertThat(hex(eutranVector, KEY_AUTN)).isEqualTo(EXPECTED_EUTRAN_AUTN_SQN_310);

                    // THEN the HSS shall download UTRAN or GERAN authentication vectors to the SGSN
                    final var utranVector = vectorsOf(answer, KEY_UTRAN_VECTOR).getFirst();
                    assertThat(hex(utranVector, KEY_AUTN)).isEqualTo(EXPECTED_UTRAN_AUTN_SQN_312);

                    assertThat(sim.getSqn()).isEqualTo(312L);

                    // THEN the ItemNumber AVP shall be present within each Vector.
                    assertThat(eutranVector.findAVP(KEY_ITEM_NUMBER).getDataAsUnsignedInt()).isEqualTo(1L);
                    assertThat(utranVector.findAVP(KEY_ITEM_NUMBER).getDataAsUnsignedInt()).isEqualTo(1L);
                }

                @Nested
                class WithTooManyVectorsRequested {
                    @Test
                    void itSendsMaxNumberOfVectors() throws Exception {
                        // GIVEN more vectors requested than the maximum number of vectors
                        final var request = buildRequest(
                                requestedAuthInfo(MAX_NUMBER_OF_VECTORS + 1L),
                                requestedAuthInfo(MAX_NUMBER_OF_VECTORS + 1L));

                        // WHEN
                        final var answer = underTest.handle(request).join();

                        // THEN the HSS shall download E-UTRAN authentication vectors to the MME
                        assertIsSuccess(answer);
                        final var eutranVectors = vectorsOf(answer, KEY_E_UTRAN_VECTOR);
                        assertThat(eutranVectors).hasSize(MAX_NUMBER_OF_VECTORS);

                        final var utranVectors = vectorsOf(answer, KEY_UTRAN_VECTOR);
                        assertThat(utranVectors).hasSize(MAX_NUMBER_OF_VECTORS);

                        // THEN the ItemNumber AVP shall be present within each Vector.
                        for (var i = 0; i < MAX_NUMBER_OF_VECTORS; i++) {
                            assertThat(eutranVectors.get(i).findAVP(KEY_ITEM_NUMBER).getDataAsUnsignedInt())
                                    .isEqualTo(i + 1L);
                        }

                        for (var i = 0; i < MAX_NUMBER_OF_VECTORS; i++) {
                            assertThat(utranVectors.get(i).findAVP(KEY_ITEM_NUMBER).getDataAsUnsignedInt())
                                    .isEqualTo(i + 1L);
                        }
                    }
                }
            }

            @Nested
            class WithResynchronizationInfoForBoth {
                @Test
                void itReturnsUnableToComplyWithoutVectors() throws Exception {
                    // GIVEN both of them include the Re-Synchronization-Info AVP
                    final var request = buildRequest(requestedAuthInfoWithResync(1L), requestedAuthInfoWithResync(1L));

                    // WHEN
                    final var answer = unwrapErrorAnswer(underTest.handle(request));

                    // THEN the HSS shall return the result code of DIAMETER_UNABLE_TO_COMPLY.
                    // UNABLE_TO_COMPLY is a base protocol result, not a 3GPP one, so it goes into Result-Code.
                    assertThat(answer.findAVP(new AVPKey(DiameterConstants.AVP_EXPERIMENTAL_RESULT, 0))).isNull();
                    assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_UNABLE_TO_COMPLY);
                }
            }
        }
    }
}
