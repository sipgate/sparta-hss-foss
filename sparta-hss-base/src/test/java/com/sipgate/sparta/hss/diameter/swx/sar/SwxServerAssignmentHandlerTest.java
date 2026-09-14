package com.sipgate.sparta.hss.diameter.swx.sar;

import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.EXP_RES_DIAMETER_ERROR_USER_UNKNOWN;
import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.VENDOR_ID_3GPP;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.EXP_RES_DIAMETER_ERROR_IDENTITY_ALREADY_REGISTERED;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.SERVER_ASSIGNMENT_ADMINISTRATIVE_DEREGISTRATION;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.SERVER_ASSIGNMENT_AUTHENTICATION_FAILURE;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.SERVER_ASSIGNMENT_AUTHENTICATION_TIMEOUT;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.SERVER_ASSIGNMENT_DEREGISTRATION_TOO_MUCH_DATA;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.SERVER_ASSIGNMENT_RE_REGISTRATION;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.SERVER_ASSIGNMENT_REGISTRATION;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.SERVER_ASSIGNMENT_TIMEOUT_DEREGISTRATION;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.SERVER_ASSIGNMENT_TIMEOUT_DEREGISTRATION_STORE_SERVER_NAME;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.SERVER_ASSIGNMENT_USER_DEREGISTRATION;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.SERVER_ASSIGNMENT_USER_DEREGISTRATION_STORE_SERVER_NAME;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.AVP_EXPERIMENTAL_RESULT_CODE;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.AVP_VENDOR_ID;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.AUTH_SESSION_STATE_NOT_MAINTAINED;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.RES_DIAMETER_MISSING_AVP;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.RES_DIAMETER_SUCCESS;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.RES_DIAMETER_UNABLE_TO_COMPLY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sipgate.sparta.diameter._3gpp.swx.messages.ServerAssignmentAnswer;
import com.sipgate.sparta.diameter._3gpp.swx.messages.ServerAssignmentRequest;
import com.sipgate.sparta.diameter.base.core.Command;
import com.sipgate.sparta.diameter.base.core.EndToEndId;
import com.sipgate.sparta.diameter.base.core.HopByHopId;
import com.sipgate.sparta.diameter.base.core.avp.AVPKey;
import com.sipgate.sparta.hss.event.EventPublisher;
import com.sipgate.sparta.hss.persistence.ImsiProfileDao;
import com.sipgate.sparta.hss.persistence.LocationVowifiDao;
import com.sipgate.sparta.hss.persistence.SimDao;
import com.sipgate.sparta.hss.persistence.entities.LocationVowifi;
import com.sipgate.sparta.hss.persistence.entities.Sim;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SwxServerAssignmentHandlerTest {

    private static final AVPKey KEY_VENDOR_ID = new AVPKey(AVP_VENDOR_ID, 0);
    private static final AVPKey KEY_EXPERIMENTAL_RESULT_CODE = new AVPKey(AVP_EXPERIMENTAL_RESULT_CODE, 0);

    private static final String IMSI = "999990000263716";
    private static final String USERNAME = IMSI + "@ims.mnc003.mcc262.3gppnetwork.org";
    private static final String ORIGIN_HOST = "aaa.example.net";
    private static final String ORIGIN_REALM = "example.net";

    // SAT values not exported as constants by the lib (SWx-specific / synthetic).
    private static final int SAT_AAA_USER_DATA_REQUEST = 12;
    private static final int SAT_PGW_UPDATE = 13;
    private static final int SAT_UNKNOWN = 99;

    @InjectMocks
    private SwxServerAssignmentHandler underTest;

    @Spy
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Mock
    private SimDao simDao;

    @Mock
    private LocationVowifiDao locationVowifiDao;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private ImsiProfileDao imsiProfileDao;

    @Mock
    private Non3gppUserDataFactory non3gppUserDataFactory;

    @BeforeEach
    void setUp() {
        // Default: Sim is present; tests override when needed.
        lenient().when(simDao.getSimByImsiString(IMSI)).thenReturn(Optional.of(new Sim()));
    }

    /**
     * Builds an incoming SWx Server-Assignment-Request via wire round-trip: assemble the outgoing
     * message, serialize it, and parse it back — exactly how the stack produces inbound requests.
     * {@code null} parameters leave the AVP out.
     */
    private static ServerAssignmentRequest.In buildSar(
        final String username, final Integer serverAssignmentType,
        final String originHost, final String originRealm) throws Exception
    {
        final var out = new ServerAssignmentRequest.Out();
        if (username != null) {
            out.setUserName(username);
        }
        if (serverAssignmentType != null) {
            out.setServerAssignmentType(serverAssignmentType);
        }
        if (originHost != null) {
            out.setOriginHost(originHost);
        }
        if (originRealm != null) {
            out.setOriginRealm(originRealm);
        }

        final var baos = new ByteArrayOutputStream();
        out.writeTo(new DataOutputStream(baos), new HopByHopId(1), new EndToEndId(2));
        return (ServerAssignmentRequest.In) Command.parseMessage(ByteBuffer.wrap(baos.toByteArray()));
    }

    private static LocationVowifi storedAaa(final String aaaServerName) {
        final var stored = new LocationVowifi();
        stored.setAaaServerName(aaaServerName);
        return stored;
    }

    private static void assertExperimentalResult(final ServerAssignmentAnswer.Out answer, final long expectedResultCode) {
        final var experimentalResult = answer.getExperimentalResult();
        assertThat(experimentalResult).as("Experimental-Result grouped AVP").isNotNull();
        assertThat(experimentalResult.findAVP(KEY_VENDOR_ID).getDataAsUnsignedInt()).isEqualTo((long) VENDOR_ID_3GPP);
        assertThat(experimentalResult.findAVP(KEY_EXPERIMENTAL_RESULT_CODE).getDataAsUnsignedInt())
            .isEqualTo(expectedResultCode);
    }

    private static void assertCommonAnswerFields(final ServerAssignmentAnswer.Out answer, final String expectedUsername) {
        // Auth-Session-State + Vendor-Specific-Application-Id are stamped by createAnswer (lib 0.1.15).
        assertThat(answer.getAuthSessionState()).isEqualTo(AUTH_SESSION_STATE_NOT_MAINTAINED);
        assertThat(answer.getVendorSpecificApplicationId()).isNotNull();
        assertThat(answer.getUserName()).isEqualTo(expectedUsername);
    }

    // --- DEREG success, AAA-Server name matches Origin-Host ---

    @ParameterizedTest
    @ValueSource(ints = {
        SERVER_ASSIGNMENT_USER_DEREGISTRATION,
        SERVER_ASSIGNMENT_ADMINISTRATIVE_DEREGISTRATION,
        SERVER_ASSIGNMENT_AUTHENTICATION_FAILURE,
        SERVER_ASSIGNMENT_AUTHENTICATION_TIMEOUT
    })
    void itDeregistersAndReturnsSuccessWhenStoredNameMatchesOriginHost(final int serverAssignmentType) throws Exception {
        // GIVEN
        final var sar = buildSar(USERNAME, serverAssignmentType, ORIGIN_HOST, ORIGIN_REALM);
        when(locationVowifiDao.getAaaServer(IMSI)).thenReturn(Optional.of(storedAaa(ORIGIN_HOST)));

        // WHEN
        final var answer = underTest.handle(sar).join();

        // THEN
        assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_SUCCESS);
        assertCommonAnswerFields(answer, USERNAME);
        // On success the 3GPP-AAA-Server-Name is NOT set on the answer.
        assertThat(answer.get3gppAaaServerName()).isNull();

        verify(locationVowifiDao).clearAaaServer(IMSI);
        final var captor = ArgumentCaptor.forClass(AaaServerAssignmentChanged.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().imsi()).isEqualTo(IMSI);
        assertThat(captor.getValue().aaaServerName()).isNull();
    }

    // --- REGISTRATION success → 2001 + Non-3GPP-User-Data ---

    @ParameterizedTest
    @ValueSource(ints = { SERVER_ASSIGNMENT_REGISTRATION, SERVER_ASSIGNMENT_RE_REGISTRATION })
    void itReturnsSuccessWithNon3gppUserDataForRegistration(final int serverAssignmentType) throws Exception {
        // GIVEN — known SIM, same-name stored (idempotent), a non-empty profile
        final var sar = buildSar(USERNAME, serverAssignmentType, ORIGIN_HOST, ORIGIN_REALM);
        when(locationVowifiDao.getAaaServer(IMSI)).thenReturn(Optional.of(storedAaa(ORIGIN_HOST)));
        when(imsiProfileDao.findByImsi(IMSI)).thenReturn(Optional.of("default"));
        when(non3gppUserDataFactory.createNon3gppUserData(any(), any()))
            .thenReturn(List.of(/* opaque profile AVPs */));

        // WHEN
        final var answer = underTest.handle(sar).join();

        // THEN
        assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_SUCCESS);
        assertThat(answer.getNon3gppUserData()).isNotNull();   // AVPContainer present
        assertCommonAnswerFields(answer, USERNAME);
    }

    // --- Step-3 mismatch: stored name != request Origin-Host → 5005 ---

    @Test
    void itReturnsIdentityAlreadyRegisteredWhenStoredNameDiffersFromOriginHost() throws Exception {
        // GIVEN
        final var storedName = "old-aaa.example.net";
        final var sar = buildSar(USERNAME, SERVER_ASSIGNMENT_USER_DEREGISTRATION, ORIGIN_HOST, ORIGIN_REALM);
        when(locationVowifiDao.getAaaServer(IMSI)).thenReturn(Optional.of(storedAaa(storedName)));

        // WHEN
        final var answer = underTest.handle(sar).join();

        // THEN
        assertExperimentalResult(answer, EXP_RES_DIAMETER_ERROR_IDENTITY_ALREADY_REGISTERED);
        assertCommonAnswerFields(answer, USERNAME);
        assertThat(answer.get3gppAaaServerName()).isEqualTo(storedName);

        verify(locationVowifiDao, never()).clearAaaServer(any());
        verifyNoInteractions(eventPublisher);
    }

    // --- Step-3 no stored AAA → UNABLE_TO_COMPLY (5012) ---

    @Test
    void itReturnsUnableToComplyWhenNoAaaServerStoredForDeregistration() throws Exception {
        // GIVEN
        final var sar = buildSar(USERNAME, SERVER_ASSIGNMENT_USER_DEREGISTRATION, ORIGIN_HOST, ORIGIN_REALM);
        when(locationVowifiDao.getAaaServer(IMSI)).thenReturn(Optional.empty());

        // WHEN
        final var answer = underTest.handle(sar).join();

        // THEN
        assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_UNABLE_TO_COMPLY);
        assertThat(answer.getExperimentalResult()).isNull();
        assertCommonAnswerFields(answer, USERNAME);

        verify(locationVowifiDao, never()).clearAaaServer(any());
        verifyNoInteractions(eventPublisher);
    }

    // --- Non-deregistration SATs → UNABLE_TO_COMPLY, no clearAaaServer ---

    private static Stream<Arguments> nonDeregistrationTypes() {
        return Stream.of(
            Arguments.of(SERVER_ASSIGNMENT_TIMEOUT_DEREGISTRATION),
            Arguments.of(SERVER_ASSIGNMENT_TIMEOUT_DEREGISTRATION_STORE_SERVER_NAME),
            Arguments.of(SERVER_ASSIGNMENT_USER_DEREGISTRATION_STORE_SERVER_NAME),
            Arguments.of(SERVER_ASSIGNMENT_DEREGISTRATION_TOO_MUCH_DATA),
            Arguments.of(SAT_AAA_USER_DATA_REQUEST),
            Arguments.of(SAT_PGW_UPDATE),
            Arguments.of(SAT_UNKNOWN)
        );
    }

    @ParameterizedTest
    @MethodSource("nonDeregistrationTypes")
    void itReturnsUnableToComplyForNonDeregistrationServerAssignmentTypes(final int serverAssignmentType) throws Exception {
        // GIVEN
        final var sar = buildSar(USERNAME, serverAssignmentType, ORIGIN_HOST, ORIGIN_REALM);

        // WHEN
        final var answer = underTest.handle(sar).join();

        // THEN
        assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_UNABLE_TO_COMPLY);
        assertThat(answer.getExperimentalResult()).isNull();
        assertCommonAnswerFields(answer, USERNAME);

        verifyNoInteractions(locationVowifiDao);
        verify(locationVowifiDao, never()).clearAaaServer(any());
        verifyNoInteractions(eventPublisher);
    }

    // --- Missing SAT (-1) → MISSING_AVP ---

    @Test
    void itReturnsMissingAvpWhenServerAssignmentTypeAbsent() throws Exception {
        // GIVEN — no Server-Assignment-Type AVP set → getServerAssignmentType() == -1
        final var sar = buildSar(USERNAME, null, ORIGIN_HOST, ORIGIN_REALM);

        // WHEN
        final var answer = underTest.handle(sar).join();

        // THEN
        assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_MISSING_AVP);
        assertThat(answer.getExperimentalResult()).isNull();
        assertCommonAnswerFields(answer, USERNAME);

        verifyNoInteractions(locationVowifiDao);
        verifyNoInteractions(eventPublisher);
    }

    // --- Missing Origin-Host (null) → MISSING_AVP (defensive; ABNF-mandatory) ---

    @Test
    void itReturnsMissingAvpWhenOriginHostAbsent() throws Exception {
        // GIVEN — a DEREG SAR without Origin-Host (which carries the AAA Server identity)
        final var sar = buildSar(USERNAME, SERVER_ASSIGNMENT_USER_DEREGISTRATION, null, ORIGIN_REALM);

        // WHEN
        final var answer = underTest.handle(sar).join();

        // THEN — MISSING_AVP, not 5005 (no stored-name comparison possible without a request name)
        assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_MISSING_AVP);
        assertThat(answer.getExperimentalResult()).isNull();
        assertCommonAnswerFields(answer, USERNAME);

        verifyNoInteractions(locationVowifiDao);
        verifyNoInteractions(eventPublisher);
    }

    // --- Unknown IMSI (Sim not found) → 5001 ---

    @Test
    void itReturnsUserUnknownWhenSimNotFoundForDeregistration() throws Exception {
        // GIVEN
        when(simDao.getSimByImsiString(IMSI)).thenReturn(Optional.empty());
        final var sar = buildSar(USERNAME, SERVER_ASSIGNMENT_USER_DEREGISTRATION, ORIGIN_HOST, ORIGIN_REALM);

        // WHEN
        final var answer = underTest.handle(sar).join();

        // THEN
        assertExperimentalResult(answer, EXP_RES_DIAMETER_ERROR_USER_UNKNOWN);
        assertCommonAnswerFields(answer, USERNAME);

        verify(locationVowifiDao, never()).getAaaServer(any());
        verify(locationVowifiDao, never()).clearAaaServer(any());
        verifyNoInteractions(eventPublisher);
    }
}
