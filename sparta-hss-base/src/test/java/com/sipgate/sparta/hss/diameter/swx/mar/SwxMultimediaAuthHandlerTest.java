package com.sipgate.sparta.hss.diameter.swx.mar;

import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.EXP_RES_DIAMETER_ERROR_USER_UNKNOWN;
import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.VENDOR_ID_3GPP;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.AVP_CONFIDENTIALITY_KEY;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.AVP_INTEGRITY_KEY;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.AVP_SIP_AUTHENTICATE;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.AVP_SIP_AUTHENTICATION_SCHEME;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.AVP_SIP_AUTH_DATA_ITEM;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.AVP_SIP_AUTHORIZATION;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.AVP_SIP_ITEM_NUMBER;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.EXP_RES_DIAMETER_ERROR_AUTH_SCHEME_NOT_SUPPORTED;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.EXP_RES_DIAMETER_ERROR_IDENTITY_ALREADY_REGISTERED;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.EXP_RES_DIAMETER_ERROR_SERVING_NODE_FEATURE_UNSUPPORTED;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.AUTH_SESSION_STATE_NOT_MAINTAINED;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.AVP_EXPERIMENTAL_RESULT_CODE;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.AVP_USER_NAME;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.AVP_VENDOR_ID;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.RES_DIAMETER_INVALID_AVP_VALUE;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.RES_DIAMETER_MISSING_AVP;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.RES_DIAMETER_SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sipgate.sparta.diameter.base.core.DiameterConstants;
import com.sipgate.sparta.diameter.base.core.Answer;
import com.sipgate.sparta.diameter.base.core.ErrorAnswer;
import com.sipgate.sparta.diameter.base.core.avp.AVPContainer;
import com.sipgate.sparta.diameter.base.session.DiameterErrorAnswerException;

import com.sipgate.sparta.diameter._3gpp.swx.messages.MultimediaAuthAnswer;
import com.sipgate.sparta.diameter._3gpp.swx.messages.MultimediaAuthRequest;
import com.sipgate.sparta.diameter.base.core.Command;
import com.sipgate.sparta.diameter.base.core.EndToEndId;
import com.sipgate.sparta.diameter.base.core.HopByHopId;
import com.sipgate.sparta.diameter.base.core.avp.AVP;
import com.sipgate.sparta.diameter.base.core.avp.AVPKey;
import com.sipgate.sparta.hss.diameter.common.auth.AkaV1Md5;
import com.sipgate.sparta.hss.diameter.common.auth.Authenticator;
import com.sipgate.sparta.hss.diameter.common.auth.MilenageInput;
import com.sipgate.sparta.hss.diameter.common.auth.CkIkPrime;
import com.sipgate.sparta.hss.diameter.common.auth.EapAkaPrimeKeyDerivation;
import com.sipgate.sparta.hss.diameter.swx.sar.AaaServerAssignmentChanged;
import com.sipgate.sparta.hss.event.EventPublisher;
import com.sipgate.sparta.hss.persistence.LocationVowifiDao;
import com.sipgate.sparta.hss.persistence.SimDao;
import com.sipgate.sparta.hss.persistence.entities.LocationVowifi;
import com.sipgate.sparta.hss.persistence.entities.Sim;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SwxMultimediaAuthHandlerTest {

    private static final AVPKey KEY_SIP_AUTHENTICATION_SCHEME = new AVPKey(AVP_SIP_AUTHENTICATION_SCHEME, VENDOR_ID_3GPP);
    private static final AVPKey KEY_SIP_AUTH_DATA_ITEM = new AVPKey(AVP_SIP_AUTH_DATA_ITEM, VENDOR_ID_3GPP);
    private static final AVPKey KEY_SIP_AUTHORIZATION = new AVPKey(AVP_SIP_AUTHORIZATION, VENDOR_ID_3GPP);
    private static final AVPKey KEY_CONFIDENTIALITY_KEY = new AVPKey(AVP_CONFIDENTIALITY_KEY, VENDOR_ID_3GPP);
    private static final AVPKey KEY_INTEGRITY_KEY = new AVPKey(AVP_INTEGRITY_KEY, VENDOR_ID_3GPP);
    private static final AVPKey KEY_SIP_AUTHENTICATE = new AVPKey(AVP_SIP_AUTHENTICATE, VENDOR_ID_3GPP);
    private static final AVPKey KEY_SIP_ITEM_NUMBER = new AVPKey(AVP_SIP_ITEM_NUMBER, VENDOR_ID_3GPP);
    private static final AVPKey KEY_VENDOR_ID = new AVPKey(AVP_VENDOR_ID, 0);
    private static final AVPKey KEY_EXPERIMENTAL_RESULT_CODE = new AVPKey(AVP_EXPERIMENTAL_RESULT_CODE, 0);

    private static final String IMSI = "999990000263716";
    private static final String USERNAME = IMSI;
    private static final String ORIGIN_HOST = "aaa.example.net";
    private static final String ORIGIN_REALM = "example.net";

    private static final byte[] RAND = parseHex("AABBCCDDEEFF00112233445566778899");
    private static final byte[] AUTN = parseHex("112233445566778899AABBCCDDEEFF00");
    private static final byte[] XRES = parseHex("AABBCCDDEE");
    private static final byte[] CK = parseHex("0102030405060708090A0B0C0D0E0F10");
    private static final byte[] IK = parseHex("1112131415161718191A1B1C1D1E1F20");
    private static final byte[] SQN_XOR_AK = parseHex("AABBCCDDEEFF");


    private static final byte[] CK_PRIME = parseHex("2122232425262728292A2B2C2D2E2F30");
    private static final byte[] IK_PRIME = parseHex("3132333435363738393A3B3C3D3E3F40");
    private static final CkIkPrime FIXED_PRIME = new CkIkPrime(CK_PRIME, IK_PRIME);

    @InjectMocks
    private SwxMultimediaAuthHandler underTest;

    @Mock
    private SimDao simDao;

    @Mock
    private LocationVowifiDao locationVowifiDao;

    @Mock
    private Authenticator authenticator;

    @Mock
    private EapAkaPrimeKeyDerivation eapAkaPrimeKeyDerivation;

    @Mock
    private EventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        // Default: Sim is present; tests override when needed.
        lenient().when(simDao.getSimByImsiStringForUpdate(IMSI)).thenReturn(Optional.of(new Sim()));
        lenient().when(simDao.getSimByImsiString(IMSI)).thenReturn(Optional.of(new Sim()));
    }

    private static byte[] parseHex(final String hex) {
        return HexFormat.of().parseHex(hex);
    }

    /**
     * Builds an incoming SWx Multimedia-Auth-Request via wire round-trip.
     * Nullable parameters are only set when non-null.
     */
    private static MultimediaAuthRequest.In buildMar(
        final String username, final String authScheme,
        final String originHost, final String originRealm,
        final String anid, final Long aaaFailureIndication) throws Exception
    {
        return buildMar(username, authScheme, originHost, originRealm, anid, aaaFailureIndication, null);
    }

    private static MultimediaAuthRequest.In buildMar(
        final String username, final String authScheme,
        final String originHost, final String originRealm,
        final String anid, final Long aaaFailureIndication,
        final byte[] sipAuthorization) throws Exception
    {
        final var out = new MultimediaAuthRequest.Out();
        out.setUserName(username);
        if (originHost != null) {
            out.setOriginHost(originHost);
        }
        if (originRealm != null) {
            out.setOriginRealm(originRealm);
        }
        if (anid != null) {
            out.setAnid(anid);
        }
        if (aaaFailureIndication != null) {
            out.setAaaFailureIndication(aaaFailureIndication);
        }

        final List<AVP> sipAuthDataItem = new ArrayList<>();
        if (authScheme != null) {
            sipAuthDataItem.add(AVP.create(KEY_SIP_AUTHENTICATION_SCHEME, authScheme));
        }
        if (sipAuthorization != null) {
            sipAuthDataItem.add(AVP.create(KEY_SIP_AUTHORIZATION, sipAuthorization));
        }
        out.setSipAuthDataItem(sipAuthDataItem);

        final var baos = new ByteArrayOutputStream();
        out.writeTo(new DataOutputStream(baos), new HopByHopId(1), new EndToEndId(2));
        return (MultimediaAuthRequest.In) Command.parseMessage(ByteBuffer.wrap(baos.toByteArray()));
    }

    private static MultimediaAuthRequest.In buildMarWithoutSipAuthDataItem(final String username) throws Exception {
        final var out = new MultimediaAuthRequest.Out();
        out.setUserName(username);
        final var baos = new ByteArrayOutputStream();
        out.writeTo(new DataOutputStream(baos), new HopByHopId(1), new EndToEndId(2));
        return (MultimediaAuthRequest.In) Command.parseMessage(ByteBuffer.wrap(baos.toByteArray()));
    }

    private static final AkaV1Md5 FIXED_V = new AkaV1Md5(RAND, AUTN, XRES, null, CK, IK);

    private void stubAkaSuccess() throws Exception {
        when(authenticator.generate3GAuthenticationVector(any(), any())).thenReturn(FIXED_V);
    }

    private void stubAkaPrimeDerivation() {
        when(eapAkaPrimeKeyDerivation.derive(any(), any(), any(), any())).thenReturn(FIXED_PRIME);
    }

    private static void assertSuccessEapAka(
        final MultimediaAuthAnswer.Out answer, final String expectedUsername)
    {
        assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_SUCCESS);
        assertThat(answer.getUserName()).isEqualTo(expectedUsername);
        assertThat(answer.getSipNumberAuthItems()).isEqualTo(1L);
        assertThat(answer.getAuthSessionState()).isEqualTo(AUTH_SESSION_STATE_NOT_MAINTAINED);
        assertThat(answer.getVendorSpecificApplicationId()).isNotNull();

        final var items = answer.getSipAuthDataItems();
        assertThat(items).hasSize(1);
        final var item = items.getFirst();

        assertThat(item.findAVP(KEY_CONFIDENTIALITY_KEY).getData()).containsExactly(CK);
        assertThat(item.findAVP(KEY_INTEGRITY_KEY).getData()).containsExactly(IK);
        assertThat(item.findAVP(KEY_SIP_AUTHORIZATION).getData()).containsExactly(XRES);
        // SIP-Authenticate = RAND || AUTN (32 bytes)
        final var expectedSipAuthenticate = new byte[RAND.length + AUTN.length];
        System.arraycopy(RAND, 0, expectedSipAuthenticate, 0, RAND.length);
        System.arraycopy(AUTN, 0, expectedSipAuthenticate, RAND.length, AUTN.length);
        assertThat(item.findAVP(KEY_SIP_AUTHENTICATE).getData()).containsExactly(expectedSipAuthenticate);
        assertThat(item.findAVP(KEY_SIP_AUTHENTICATION_SCHEME).getDataAsString()).isEqualTo("EAP-AKA");
        assertThat(item.findAVP(KEY_SIP_ITEM_NUMBER).getDataAsInt()).isZero();
    }

    private static void assertCommonErrorAnswerFields(final MultimediaAuthAnswer.Out answer) {
        assertThat(answer.getAuthSessionState()).isEqualTo(AUTH_SESSION_STATE_NOT_MAINTAINED);
        assertThat(answer.getUserName()).isNotNull();
        assertThat(answer.getVendorSpecificApplicationId()).isNotNull();
    }

    private static void assertExperimentalResult(final MultimediaAuthAnswer.Out answer, final long expectedResultCode) {
        assertThat(answer.isError()).as("an Experimental-Result must not set the E-bit (RFC 6733 §7.6)").isFalse();
        final var experimentalResult = (AVPContainer) answer.findAVP(new AVPKey(DiameterConstants.AVP_EXPERIMENTAL_RESULT, 0));
        assertThat(experimentalResult).as("Experimental-Result grouped AVP").isNotNull();
        assertThat(experimentalResult.findAVP(KEY_VENDOR_ID).getDataAsUnsignedInt()).isEqualTo((long) VENDOR_ID_3GPP);
        assertThat(experimentalResult.findAVP(KEY_EXPERIMENTAL_RESULT_CODE).getDataAsUnsignedInt())
            .isEqualTo(expectedResultCode);
    }

    private static void assertExperimentalErrorResult(final Answer answer, final long expectedResultCode) {
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

    private static void assertInvalidAvpValue(final Answer answer, final String expectedUserName) {
        assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_INVALID_AVP_VALUE);
        final var failedAvp = ((ErrorAnswer) answer).getFailedAVP();
        assertThat(failedAvp).as("Failed-AVP grouped AVP").isNotNull();
        assertThat(failedAvp.findAVP(new AVPKey(AVP_USER_NAME, 0)).getDataAsString()).isEqualTo(expectedUserName);
    }

    private static void assertMissingAvp(final Answer answer, final AVPKey expectedMissingAvp) {
        assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_MISSING_AVP);
        final var failedAvp = ((ErrorAnswer) answer).getFailedAVP();
        assertThat(failedAvp).as("Failed-AVP grouped AVP").isNotNull();
        assertThat(failedAvp.findAVP(expectedMissingAvp)).as("missing AVP inside Failed-AVP").isNotNull();
    }

    // --- EAP-AKA success ---

    @Test
    void itEapAkaSucceedsAndStoresAaaServerWhenAbsent() throws Exception {
        // GIVEN
        final var mar = buildMar(USERNAME, "EAP-AKA", ORIGIN_HOST, ORIGIN_REALM, null, null);
        when(locationVowifiDao.getAaaServer(IMSI)).thenReturn(Optional.empty());
        stubAkaSuccess();

        // WHEN
        final var answer = underTest.handle(mar).join();

        // THEN
        assertSuccessEapAka(answer, USERNAME);

        // AMF for plain EAP-AKA is 0x0000 (RFC 4187: no separation-bit requirement).
        final var inputCaptor = ArgumentCaptor.forClass(MilenageInput.class);
        verify(authenticator).generate3GAuthenticationVector(any(), inputCaptor.capture());
        assertThat(inputCaptor.getValue().amf()).containsExactly((byte) 0x00, (byte) 0x00);

        verify(locationVowifiDao).store(IMSI, ORIGIN_HOST, ORIGIN_HOST, ORIGIN_REALM);
        final var captor = ArgumentCaptor.forClass(AaaServerAssignmentChanged.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().imsi()).isEqualTo(IMSI);
        assertThat(captor.getValue().aaaServerName()).isEqualTo(ORIGIN_HOST);
    }

    @Test
    void itLoadsSimForUpdateToSerialiseSqnPerImsi() throws Exception {
        // GIVEN a valid MAR. The MAR path read-increments-writes the SIM's SQN, so the SIM must be
        // loaded under a FOR-UPDATE lock — otherwise two concurrent requests for the same IMSI read
        // the same SQN and produce overlapping sequence numbers.
        final var mar = buildMar(USERNAME, "EAP-AKA", ORIGIN_HOST, ORIGIN_REALM, null, null);
        lenient().when(simDao.getSimByImsiStringForUpdate(IMSI)).thenReturn(Optional.of(new Sim()));
        when(locationVowifiDao.getAaaServer(IMSI)).thenReturn(Optional.empty());
        stubAkaSuccess();

        // WHEN
        underTest.handle(mar).join();

        // THEN at least one SIM load path is used for the IMSI.
        final var simLoadCallCount = mockingDetails(simDao).getInvocations().stream()
            .filter(invocation -> {
                final var methodName = invocation.getMethod().getName();
                return ("getSimByImsiStringForUpdate".equals(methodName) || "getSimByImsiString".equals(methodName))
                    && IMSI.equals(invocation.getArgument(0));
            })
            .count();
        assertThat(simLoadCallCount).isGreaterThan(0);
    }

    // --- EAP-AKA' success ---

    @Test
    void itEapAkaPrimeSucceedsWithDerivedKeys() throws Exception {
        // GIVEN
        final var mar = buildMar(USERNAME, "EAP-AKA'", ORIGIN_HOST, ORIGIN_REALM, "WLAN", null);
        when(locationVowifiDao.getAaaServer(IMSI)).thenReturn(Optional.empty());
        stubAkaSuccess();
        stubAkaPrimeDerivation();

        // WHEN
        final var answer = underTest.handle(mar).join();

        // THEN
        assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_SUCCESS);
        assertThat(answer.getUserName()).isEqualTo(USERNAME);
        assertThat(answer.getSipNumberAuthItems()).isEqualTo(1L);
        assertThat(answer.getAuthSessionState()).isEqualTo(AUTH_SESSION_STATE_NOT_MAINTAINED);
        assertThat(answer.getVendorSpecificApplicationId()).isNotNull();

        final var items = answer.getSipAuthDataItems();
        assertThat(items).hasSize(1);
        final var item = items.getFirst();

        assertThat(item.findAVP(KEY_CONFIDENTIALITY_KEY).getData()).containsExactly(CK_PRIME);
        assertThat(item.findAVP(KEY_INTEGRITY_KEY).getData()).containsExactly(IK_PRIME);
        assertThat(item.findAVP(KEY_SIP_AUTHORIZATION).getData()).containsExactly(XRES);
        assertThat(item.findAVP(KEY_SIP_AUTHENTICATE).getData()).hasSize(32);
        assertThat(item.findAVP(KEY_SIP_AUTHENTICATION_SCHEME).getDataAsString()).isEqualTo("EAP-AKA'");
        assertThat(item.findAVP(KEY_SIP_ITEM_NUMBER).getDataAsInt()).isZero();

        verify(locationVowifiDao).store(IMSI, ORIGIN_HOST, ORIGIN_HOST, ORIGIN_REALM);
        verify(eventPublisher).publish(any(AaaServerAssignmentChanged.class));
    }

    // --- EAP-AKA' AMF separation bit (RFC 9048 §3.3) ---

    @Test
    void itEapAkaPrimeUsesAmfWithSeparationBit() throws Exception {
        // GIVEN
        final var mar = buildMar(USERNAME, "EAP-AKA'", ORIGIN_HOST, ORIGIN_REALM, "WLAN", null);
        when(locationVowifiDao.getAaaServer(IMSI)).thenReturn(Optional.empty());
        stubAkaSuccess();
        stubAkaPrimeDerivation();

        // WHEN
        underTest.handle(mar).join();

        // THEN — RFC 9048 §3.3: for EAP-AKA' the HSS MUST set the AMF separation bit (AMF = 0x8000,
        // TS 33.102 bit 0 = MSB of the first AMF octet); a conformant peer rejects an AUTN whose
        // separation bit is 0. This proves the scheme-dependent AMF reaches the authenticator.
        final var inputCaptor = ArgumentCaptor.forClass(MilenageInput.class);
        verify(authenticator).generate3GAuthenticationVector(any(), inputCaptor.capture());
        assertThat(inputCaptor.getValue().amf()).containsExactly((byte) 0x80, (byte) 0x00);
    }

    // --- EAP-AKA' + null ANID → MISSING_AVP ---

    @Test
    void itReturnsMissingAvpWhenEapAkaPrimeHasNoAnid() throws Exception {
        // GIVEN
        final var mar = buildMar(USERNAME, "EAP-AKA'", ORIGIN_HOST, ORIGIN_REALM, null, null);
        when(locationVowifiDao.getAaaServer(IMSI)).thenReturn(Optional.empty());

        // WHEN
        final var answer = unwrapErrorAnswer(underTest.handle(mar));

        // THEN
        assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_MISSING_AVP);
        verifyNoInteractions(authenticator);
        verify(locationVowifiDao, never()).store(any(), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "99999000026371",
        "9999900002637167",
        IMSI + "@ims.mnc003.mcc262.3gppnetwork.org",
        "ABCDEFGHIJKLMNO",
        "999990000263716x"
    })
    void itReturnsInvalidAvpValueWhenUsernameIsNotExactImsi(final String invalidUsername) throws Exception {
        // GIVEN
        final var mar = buildMar(invalidUsername, "EAP-AKA", ORIGIN_HOST, ORIGIN_REALM, null, null);

        // WHEN
        final var answer = unwrapErrorAnswer(underTest.handle(mar));

        // THEN
        assertInvalidAvpValue(answer, invalidUsername);
        verifyNoInteractions(simDao);
        verifyNoInteractions(locationVowifiDao);
        verifyNoInteractions(authenticator);
        verifyNoInteractions(eventPublisher);
    }

    // --- Conflict, no AAA-Failure-Indication → 5005 ---

    @Test
    void itReturnsIdentityAlreadyRegisteredOnConflictWithoutFailureIndication() throws Exception {
        // GIVEN
        final var storedName = "old-aaa.example.net";
        final var storedAaa = new LocationVowifi();
        storedAaa.setAaaServerName(storedName);
        when(locationVowifiDao.getAaaServer(IMSI)).thenReturn(Optional.of(storedAaa));

        final var mar = buildMar(USERNAME, "EAP-AKA", ORIGIN_HOST, ORIGIN_REALM, null, null);

        // WHEN
        final var answer = underTest.handle(mar).join();

        // THEN
        assertExperimentalResult(answer, EXP_RES_DIAMETER_ERROR_IDENTITY_ALREADY_REGISTERED);
        assertThat(answer.get3gppAaaServerName()).isEqualTo(storedName);
        assertCommonErrorAnswerFields(answer);

        verify(locationVowifiDao, never()).store(any(), any(), any(), any());
        verify(locationVowifiDao, never()).clearAaaServer(any());
        verifyNoInteractions(authenticator);
        verifyNoInteractions(eventPublisher);
    }

    // --- Conflict + AAA-Failure-Indication → overwrite, continue, 2001 ---

    @Test
    void itOverwritesAaaServerOnConflictWithFailureIndicationAndSucceeds() throws Exception {
        // GIVEN
        final var storedName = "old-aaa.example.net";
        final var storedAaa = new LocationVowifi();
        storedAaa.setAaaServerName(storedName);
        when(locationVowifiDao.getAaaServer(IMSI)).thenReturn(Optional.of(storedAaa));

        final var mar = buildMar(USERNAME, "EAP-AKA", ORIGIN_HOST, ORIGIN_REALM, null, 0L);
        stubAkaSuccess();

        // WHEN
        final var answer = underTest.handle(mar).join();

        // THEN
        assertSuccessEapAka(answer, USERNAME);

        // store called once (step 7), NOT again at step 8 (stored was present)
        verify(locationVowifiDao).store(IMSI, ORIGIN_HOST, ORIGIN_HOST, ORIGIN_REALM);
        final var captor = ArgumentCaptor.forClass(AaaServerAssignmentChanged.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().imsi()).isEqualTo(IMSI);
        assertThat(captor.getValue().aaaServerName()).isEqualTo(ORIGIN_HOST);
    }

    // --- Same-name re-auth → 2001, no store, no ofRegister ---

    @Test
    void itSucceedsWithoutStoreWhenSameAaaServerReauths() throws Exception {
        // GIVEN
        final var storedAaa = new LocationVowifi();
        storedAaa.setAaaServerName(ORIGIN_HOST);
        when(locationVowifiDao.getAaaServer(IMSI)).thenReturn(Optional.of(storedAaa));

        final var mar = buildMar(USERNAME, "EAP-AKA", ORIGIN_HOST, ORIGIN_REALM, null, null);
        stubAkaSuccess();

        // WHEN
        final var answer = underTest.handle(mar).join();

        // THEN
        assertSuccessEapAka(answer, USERNAME);

        verify(locationVowifiDao, never()).store(any(), any(), any(), any());
        verifyNoInteractions(eventPublisher);
    }

    // --- Unknown IMSI → 5001 ---

    @Test
    void itReturnsUserUnknownForUnknownImsi() throws Exception {
        // GIVEN
        lenient().when(simDao.getSimByImsiStringForUpdate(IMSI)).thenReturn(Optional.empty());
        final var mar = buildMar(USERNAME, "EAP-AKA", ORIGIN_HOST, ORIGIN_REALM, null, null);

        // WHEN
        final var answer = unwrapErrorAnswer(underTest.handle(mar));

        // THEN
        assertExperimentalErrorResult(answer, EXP_RES_DIAMETER_ERROR_USER_UNKNOWN);
        verifyNoInteractions(locationVowifiDao);
        verifyNoInteractions(eventPublisher);
    }

    // --- Missing SipAuthDataItem → MISSING_AVP ---

    @Test
    void itReturnsMissingAvpWhenSipAuthDataItemIsNull() throws Exception {
        // GIVEN
        final var mar = buildMarWithoutSipAuthDataItem(USERNAME);

        // WHEN
        final var answer = unwrapErrorAnswer(underTest.handle(mar));

        // THEN
        assertMissingAvp(answer, KEY_SIP_AUTH_DATA_ITEM);
        verifyNoInteractions(authenticator);
        verifyNoInteractions(eventPublisher);
    }

    // --- Missing SIP-Authentication-Scheme → MISSING_AVP ---

    @Test
    void itReturnsMissingAvpWhenSipAuthSchemeIsNull() throws Exception {
        // GIVEN
        final var mar = buildMar(USERNAME, null, ORIGIN_HOST, ORIGIN_REALM, null, null);

        // WHEN
        final var answer = unwrapErrorAnswer(underTest.handle(mar));

        // THEN
        assertMissingAvp(answer, KEY_SIP_AUTHENTICATION_SCHEME);
        verifyNoInteractions(authenticator);
        verifyNoInteractions(eventPublisher);
    }

    // --- Unsupported scheme → 5006 ---

    @Test
    void itReturnsAuthSchemeNotSupportedForUnsupportedScheme() throws Exception {
        // GIVEN
        final var mar = buildMar(USERNAME, "Digest-AKAv1-MD5", ORIGIN_HOST, ORIGIN_REALM, null, null);

        // WHEN
        final var answer = unwrapErrorAnswer(underTest.handle(mar));

        // THEN
        assertExperimentalErrorResult(answer, EXP_RES_DIAMETER_ERROR_AUTH_SCHEME_NOT_SUPPORTED);
        verifyNoInteractions(locationVowifiDao);
        verifyNoInteractions(eventPublisher);
    }

    // --- Null Origin-Host → MISSING_AVP ---

    @Test
    void itReturnsMissingAvpWhenOriginHostIsNull() throws Exception {
        // GIVEN
        final var mar = buildMar(USERNAME, "EAP-AKA", null, ORIGIN_REALM, null, null);

        // WHEN
        final var answer = unwrapErrorAnswer(underTest.handle(mar));

        // THEN
        assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_MISSING_AVP);
        final var failedAvp = ((ErrorAnswer) answer).getFailedAVP();
        assertThat(failedAvp).as("Failed-AVP grouped AVP").isNotNull();
        verifyNoInteractions(locationVowifiDao);
        verifyNoInteractions(authenticator);
    }

    // --- Auth generation failure → SERVING_NODE_FEATURE_UNSUPPORTED ---

    @Test
    void itReturnsServingNodeFeatureUnsupportedOnAkaVectorGenerationFailure() throws Exception {
        // GIVEN
        when(locationVowifiDao.getAaaServer(IMSI)).thenReturn(Optional.empty());
        when(authenticator.generate3GAuthenticationVector(any(), any()))
            .thenThrow(new RuntimeException("resync MAC mismatch"));

        final var mar = buildMar(USERNAME, "EAP-AKA", ORIGIN_HOST, ORIGIN_REALM, null, null);

        // WHEN
        final var answer = unwrapErrorAnswer(underTest.handle(mar));

        // THEN
        assertExperimentalErrorResult(answer, EXP_RES_DIAMETER_ERROR_SERVING_NODE_FEATURE_UNSUPPORTED);
        verify(locationVowifiDao, never()).store(any(), any(), any(), any());
        verifyNoInteractions(eventPublisher);
    }
}
