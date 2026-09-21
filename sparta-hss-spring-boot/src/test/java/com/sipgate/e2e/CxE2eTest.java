package com.sipgate.e2e;

import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.VENDOR_ID_3GPP;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.AVP_CONFIDENTIALITY_KEY;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.AVP_INTEGRITY_KEY;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.AVP_REASON_CODE;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.AVP_SIP_AUTHENTICATE;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.AVP_SIP_AUTHENTICATION_SCHEME;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.AVP_SIP_AUTHORIZATION;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.AVP_SIP_ITEM_NUMBER;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.REASON_CODE_NEW_SERVER_ASSIGNED;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.SERVER_ASSIGNMENT_REGISTRATION;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.SERVER_ASSIGNMENT_USER_DEREGISTRATION;
import static jakarta.xml.bind.DatatypeConverter.parseHexBinary;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.sipgate.e2e.requests.CxRequestFactory;
import com.sipgate.e2e.utils.SubscriberDb;
import com.sipgate.sparta.diameter._3gpp.common._3gppConstants;
import com.sipgate.sparta.diameter._3gpp.cxdx.messages.RegistrationTerminationRequest;
import com.sipgate.sparta.diameter.base.core.DiameterConstants;
import com.sipgate.sparta.diameter.base.core.avp.AVPContainer;
import com.sipgate.sparta.diameter.base.core.avp.AVPKey;
import com.sipgate.sparta.hss.diameter.common.auth.AkaV1Md5;
import com.sipgate.sparta.hss.diameter.common.auth.MilenageAuthenticator;
import com.sipgate.sparta.hss.diameter.common.auth.MilenageInput;
import com.sipgate.sparta.hss.diameter.common.auth.MilenageLogger;
import com.sipgate.sparta.hss.diameter.common.auth.RandomGenerator;
import com.sipgate.sparta.hss.diameter.common.auth.StoredKeyFormat;
import com.sipgate.sparta.hss.persistence.entities.Sim;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CxE2eTest extends AbstractDiameterE2eTest {

    private static final String SEEDED_IMSI = "001010000000001";
    private static final String SEEDED_MSISDN = "4915790000001";
    private static final String SEEDED_PRIVATE_IDENTITY = SEEDED_IMSI + "@ims.mnc001.mcc001.3gppnetwork.org";
    private static final String SEEDED_PUBLIC_IDENTITY = "tel:+" + SEEDED_MSISDN;

    private static final String UNKNOWN_PRIVATE_IDENTITY = "001019999999999@ims.mnc001.mcc001.3gppnetwork.org";

    /** A previously-registered S-CSCF that differs from the agent's; a MAR reassignment deregisters it. */
    private static final String OLD_SCSCF_NAME = "sip:old-scscf.ims.mnc001.mcc001.3gppnetwork.org:5060";
    private static final String OLD_SCSCF_HOST = "old-scscf.ims.mnc001.mcc001.3gppnetwork.org";

    /// The IP-SM-GW identity a UCN advertises in the SAR's Serving-Node AVP.
    private static final String UCN_IP_SM_GW_NAME = "ucn01.epc.mnc001.mcc001.3gppnetwork.org";
    private static final String UCN_IP_SM_GW_REALM = "epc.mnc001.mcc001.3gppnetwork.org";

    /// A second IP-SM-GW identity, to prove a re-registration overwrites the stored one.
    private static final String UCN_IP_SM_GW_NAME_2 = "ucn02.epc.mnc001.mcc001.3gppnetwork.org";
    private static final String UCN_IP_SM_GW_REALM_2 = "epc2.mnc001.mcc001.3gppnetwork.org";

    private static final byte[] TEST_KI = parseHexBinary("000102030405060708090A0B0C0D0E0F");
    private static final byte[] TEST_OP = parseHexBinary("0F0E0D0C0B0A09080706050403020100");
    private static final long   TEST_SQN = 96L; // >= 32, deterministic

    /// The RTR is published asynchronously, so "no RTR" needs a moment to be meaningful.
    private static final Duration NO_RTR_GRACE = Duration.ofSeconds(1);

    /** IMS AKA uses AMF 0000 (TS 33.203 §6.1), unlike the 8000 used for E-UTRAN. */
    private static final byte[] AMF_IMS = parseHexBinary("0000");

    private static final AVPKey KEY_SIP_AUTHENTICATION_SCHEME = new AVPKey(AVP_SIP_AUTHENTICATION_SCHEME, VENDOR_ID_3GPP);
    private static final AVPKey KEY_SIP_AUTHENTICATE = new AVPKey(AVP_SIP_AUTHENTICATE, VENDOR_ID_3GPP);
    private static final AVPKey KEY_SIP_AUTHORIZATION = new AVPKey(AVP_SIP_AUTHORIZATION, VENDOR_ID_3GPP);
    private static final AVPKey KEY_CONFIDENTIALITY_KEY = new AVPKey(AVP_CONFIDENTIALITY_KEY, VENDOR_ID_3GPP);
    private static final AVPKey KEY_INTEGRITY_KEY = new AVPKey(AVP_INTEGRITY_KEY, VENDOR_ID_3GPP);
    private static final AVPKey KEY_SIP_ITEM_NUMBER = new AVPKey(AVP_SIP_ITEM_NUMBER, VENDOR_ID_3GPP);

    /// Every Cx test starts from "no S-CSCF stored"; tests that need a prior registration seed it
    /// explicitly. This also keeps the MAR happy-path free of a stray RTR side effect.
    @BeforeEach
    void clearImsRegistration() {
        SubscriberDb.clearScscf(SEEDED_IMSI);
        SubscriberDb.clearIpSmGw(SEEDED_IMSI);
    }

    @Nested
    class Mar {
        @Test
        void knownImsSubscriber_returnsVerifiableAkaVector() throws Exception {
            // GIVEN a subscriber with known Ki/OP/SQN so the vector is deterministic given the RAND
            SubscriberDb.setSimAuthKeys(SEEDED_IMSI, TEST_KI, TEST_OP, TEST_SQN);

            // WHEN
            final var mar = CxRequestFactory.multimediaAuth(SEEDED_PRIVATE_IDENTITY);
            final var maa = agent().sendAndWait(mar, Duration.ofSeconds(5));

            // THEN success, the right subscriber, exactly one AKAv1-MD5 auth item
            assertThat(maa.getResultCode()).isEqualTo(DiameterConstants.RES_DIAMETER_SUCCESS);
            assertThat(maa.getUserName()).isEqualTo(SEEDED_PRIVATE_IDENTITY);
            assertThat(maa.getPublicIdentity()).isEqualTo(SEEDED_PUBLIC_IDENTITY);
            assertThat(maa.getSipNumberAuthItems()).isEqualTo(1L);

            final var items = maa.getSipAuthDataItems();
            assertThat(items).as("SIP-Auth-Data-Item").hasSize(1);
            final var item = items.getFirst();

            assertThat(item.findAVP(KEY_SIP_AUTHENTICATION_SCHEME).getDataAsString())
                .isEqualTo(CxRequestFactory.SIP_AUTH_SCHEME_AKA_V1_MD5);
            assertThat(item.findAVP(KEY_SIP_ITEM_NUMBER).getDataAsInt()).isZero();

            final var sipAuthenticate = item.findAVP(KEY_SIP_AUTHENTICATE).getDataAsOctetString();
            final var xres = item.findAVP(KEY_SIP_AUTHORIZATION).getDataAsOctetString();
            final var ck = item.findAVP(KEY_CONFIDENTIALITY_KEY).getDataAsOctetString();
            final var ik = item.findAVP(KEY_INTEGRITY_KEY).getDataAsOctetString();
            assertThat(sipAuthenticate).as("SIP-Authenticate = RAND || AUTN").hasSize(32);
            assertThat(ck).hasSize(16);
            assertThat(ik).hasSize(16);

            final var rand = Arrays.copyOfRange(sipAuthenticate, 0, 16);
            final var autn = Arrays.copyOfRange(sipAuthenticate, 16, 32);

            // recompute the whole vector from the returned RAND + the keys/SQN we set -> must match
            final var sim = new Sim();
            sim.setSecretKey(TEST_KI);
            sim.setOp(TEST_OP);
            sim.setSqn(TEST_SQN);
            final var authenticator = new MilenageAuthenticator(fixedRand(rand), new SimpleMeterRegistry(), StoredKeyFormat.OP);
            final AkaV1Md5 expected;
            try (final var logger = new MilenageLogger(SEEDED_IMSI)) {
                expected = authenticator.generate3GAuthenticationVector(logger, new MilenageInput(AMF_IMS, null, sim));
            }
            assertThat(rand).as("RAND fed back into the oracle").isEqualTo(expected.rand());
            assertThat(autn).as("AUTN").isEqualTo(expected.autn());
            assertThat(xres).as("XRES / SIP-Authorization").isEqualTo(expected.xres());
            assertThat(ck).as("Confidentiality-Key").isEqualTo(expected.confidentialityKey());
            assertThat(ik).as("Integrity-Key").isEqualTo(expected.integrityKey());
        }

        @Test
        void noPreviousScscf_sendsNoRegistrationTermination() {
            // GIVEN no stored S-CSCF assignment at all (see clearImsRegistration)
            SubscriberDb.setSimAuthKeys(SEEDED_IMSI, TEST_KI, TEST_OP, TEST_SQN);

            final var maa = agent().sendAndWait(CxRequestFactory.multimediaAuth(SEEDED_PRIVATE_IDENTITY), Duration.ofSeconds(5));

            assertThat(maa.getResultCode()).isEqualTo(DiameterConstants.RES_DIAMETER_SUCCESS);
            assertThat(agent().pollRequest(RegistrationTerminationRequest.In.class, NO_RTR_GRACE))
                .as("RTR without a previous assignment").isEmpty();
        }

        @Test
        void unchangedScscf_sendsNoRegistrationTermination() {
            // GIVEN the stored assignment is exactly the S-CSCF the MAR comes from
            SubscriberDb.setSimAuthKeys(SEEDED_IMSI, TEST_KI, TEST_OP, TEST_SQN);
            SubscriberDb.setScscf(SEEDED_IMSI, CxRequestFactory.SCSCF_SERVER_NAME, CxRequestFactory.SCSCF_HOST, CxRequestFactory.SCSCF_REALM);

            final var maa = agent().sendAndWait(CxRequestFactory.multimediaAuth(SEEDED_PRIVATE_IDENTITY), Duration.ofSeconds(5));

            assertThat(maa.getResultCode()).isEqualTo(DiameterConstants.RES_DIAMETER_SUCCESS);
            assertThat(agent().pollRequest(RegistrationTerminationRequest.In.class, NO_RTR_GRACE))
                .as("RTR for an unchanged S-CSCF").isEmpty();
            assertThat(SubscriberDb.scscf(SEEDED_IMSI)).as("S-CSCF must stay assigned")
                .isEqualTo(CxRequestFactory.SCSCF_SERVER_NAME);
        }

        @Test
        void changedScscf_sendsRegistrationTerminationToPreviousScscf() {
            // GIVEN the subscriber is registered at a *different* S-CSCF than the MAR comes from
            SubscriberDb.setSimAuthKeys(SEEDED_IMSI, TEST_KI, TEST_OP, TEST_SQN);
            SubscriberDb.setScscf(SEEDED_IMSI, OLD_SCSCF_NAME, OLD_SCSCF_HOST, CxRequestFactory.SCSCF_REALM);

            final var maa = agent().sendAndWait(CxRequestFactory.multimediaAuth(SEEDED_PRIVATE_IDENTITY), Duration.ofSeconds(5));

            assertThat(maa.getResultCode()).isEqualTo(DiameterConstants.RES_DIAMETER_SUCCESS);
            final var rtr = agent().awaitRequest(RegistrationTerminationRequest.In.class, Duration.ofSeconds(5));
            assertThat(rtr.getUserName()).isEqualTo(SEEDED_PRIVATE_IDENTITY);
            assertThat(rtr.getDestinationHost()).as("RTR goes to the previous S-CSCF").isEqualTo(OLD_SCSCF_HOST);
            // deliberately not asserting null vs. the new name here: 29.228 §6.3.1 says overwrite,
            // the Rtr test pins whatever the handler currently does
            assertThat(SubscriberDb.scscf(SEEDED_IMSI)).as("stale assignment must be gone")
                .isNotEqualTo(OLD_SCSCF_NAME);
        }

        @Test
        void unknownImsSubscriber_returnsUserUnknown() {
            final var maa = agent().sendAndWait(
                CxRequestFactory.multimediaAuth(UNKNOWN_PRIVATE_IDENTITY), Duration.ofSeconds(5));

            // a 3GPP Experimental-Result rides in a regular answer, no E-bit (RFC 6733 §7.6)
            assertThat(maa.isError()).as("E-bit").isFalse();
            assertThat(maa.getResultCode()).isEqualTo(-1L);
            assertExperimentalResult(maa.getExperimentalResult(), _3gppConstants.EXP_RES_DIAMETER_ERROR_USER_UNKNOWN);
            assertThat(maa.getAuthSessionState()).as("Auth-Session-State")
                .isEqualTo(DiameterConstants.AUTH_SESSION_STATE_NOT_MAINTAINED);
            assertThat(maa.getVendorSpecificApplicationId()).as("Vendor-Specific-Application-Id").isNotNull();
        }
    }

    @Nested
    class Sar {
        @Test
        void registration_storesScscfAndReturnsUserData() {
            final var sar = CxRequestFactory.serverAssignment(SEEDED_PRIVATE_IDENTITY, SEEDED_PUBLIC_IDENTITY, SERVER_ASSIGNMENT_REGISTRATION);

            final var saa = agent().sendAndWait(sar, Duration.ofSeconds(5));

            assertThat(saa.getResultCode()).isEqualTo(DiameterConstants.RES_DIAMETER_SUCCESS);
            // 3GPP mandates loose routing for IMS (TS 29.228)
            assertThat(saa.getLooseRouteIndication()).isEqualTo(1);

            // the User-Data profile round-tripped and resolved the right subscriber
            final var userData = saa.getUserData();
            assertThat(userData).as("User-Data").isNotNull();
            assertThat(new String(userData, UTF_8))
                .contains(SEEDED_PRIVATE_IDENTITY)
                .contains(SEEDED_PUBLIC_IDENTITY);

            // the S-CSCF assignment was persisted for the subscriber
            assertThat(SubscriberDb.scscf(SEEDED_IMSI)).isEqualTo(CxRequestFactory.SCSCF_SERVER_NAME);
        }

        @Test
        void userDeregistration_clearsScscfAndReturnsSuccess() {
            SubscriberDb.setScscf(SEEDED_IMSI, CxRequestFactory.SCSCF_SERVER_NAME, CxRequestFactory.SCSCF_HOST, CxRequestFactory.SCSCF_REALM);

            final var sar = CxRequestFactory.serverAssignment(SEEDED_PRIVATE_IDENTITY, SEEDED_PUBLIC_IDENTITY, SERVER_ASSIGNMENT_USER_DEREGISTRATION);

            final var saa = agent().sendAndWait(sar, Duration.ofSeconds(5));

            assertThat(saa.getResultCode()).isEqualTo(DiameterConstants.RES_DIAMETER_SUCCESS);
            assertThat(SubscriberDb.scscf(SEEDED_IMSI)).as("S-CSCF after de-registration").isNull();
        }


        @Test
        void registration_overwritesExistingScscf() {
            // GIVEN the subscriber is registered at a *different* S-CSCF than the SAR comes from
            SubscriberDb.setScscf(SEEDED_IMSI, OLD_SCSCF_NAME, OLD_SCSCF_HOST, CxRequestFactory.SCSCF_REALM);

            final var sar = CxRequestFactory.serverAssignment(SEEDED_PRIVATE_IDENTITY, SEEDED_PUBLIC_IDENTITY, SERVER_ASSIGNMENT_REGISTRATION);

            final var saa = agent().sendAndWait(sar, Duration.ofSeconds(5));

            assertThat(saa.getResultCode()).isEqualTo(DiameterConstants.RES_DIAMETER_SUCCESS);
            // the new registration overwrote the stale assignment (single row, PK is imsiId)
            assertThat(SubscriberDb.scscf(SEEDED_IMSI))
                .isEqualTo(CxRequestFactory.SCSCF_SERVER_NAME)
                .isNotEqualTo(OLD_SCSCF_NAME);
        }

        @Test
        void registrationWithServingNode_overwritesExistingIpSmGw() {
            // GIVEN a first registration stored the UCN's IP-SM-GW
            final var first = CxRequestFactory.serverAssignment(
                SEEDED_PRIVATE_IDENTITY, SEEDED_PUBLIC_IDENTITY, SERVER_ASSIGNMENT_REGISTRATION,
                UCN_IP_SM_GW_NAME, UCN_IP_SM_GW_REALM);
            assertThat(agent().sendAndWait(first, Duration.ofSeconds(5)).getResultCode())
                .isEqualTo(DiameterConstants.RES_DIAMETER_SUCCESS);
            assertThat(SubscriberDb.ipSmGwName(SEEDED_IMSI)).isEqualTo(UCN_IP_SM_GW_NAME);

            // WHEN a re-registration advertises a different IP-SM-GW
            final var second = CxRequestFactory.serverAssignment(
                SEEDED_PRIVATE_IDENTITY, SEEDED_PUBLIC_IDENTITY, SERVER_ASSIGNMENT_REGISTRATION,
                UCN_IP_SM_GW_NAME_2, UCN_IP_SM_GW_REALM_2);
            assertThat(agent().sendAndWait(second, Duration.ofSeconds(5)).getResultCode())
                .isEqualTo(DiameterConstants.RES_DIAMETER_SUCCESS);

            // THEN the single row (PK is imsiId) holds the new gateway, not the old one
            assertThat(SubscriberDb.ipSmGwName(SEEDED_IMSI))
                .isEqualTo(UCN_IP_SM_GW_NAME_2)
                .isNotEqualTo(UCN_IP_SM_GW_NAME);
        }

        @Test
        void registrationWithServingNode_storesIpSmGw() {
            final var sar = CxRequestFactory.serverAssignment(
                SEEDED_PRIVATE_IDENTITY, SEEDED_PUBLIC_IDENTITY, SERVER_ASSIGNMENT_REGISTRATION,
                UCN_IP_SM_GW_NAME, UCN_IP_SM_GW_REALM);

            final var saa = agent().sendAndWait(sar, Duration.ofSeconds(5));

            assertThat(saa.getResultCode()).isEqualTo(DiameterConstants.RES_DIAMETER_SUCCESS);
            assertThat(SubscriberDb.ipSmGwName(SEEDED_IMSI)).isEqualTo(UCN_IP_SM_GW_NAME);
        }

        @Test
        void registrationWithoutServingNode_keepsTableEmpty() {
            final var sar = CxRequestFactory.serverAssignment(SEEDED_PRIVATE_IDENTITY, SEEDED_PUBLIC_IDENTITY, SERVER_ASSIGNMENT_REGISTRATION);

            final var saa = agent().sendAndWait(sar, Duration.ofSeconds(5));

            assertThat(saa.getResultCode()).isEqualTo(DiameterConstants.RES_DIAMETER_SUCCESS);
            assertThat(SubscriberDb.ipSmGwName(SEEDED_IMSI)).isNull();
        }

        @Test
        void userDeregistration_clearsIpSmGw() {
            // GIVEN a subscriber registered with an IP-SM-GW assignment
            SubscriberDb.setScscf(SEEDED_IMSI, CxRequestFactory.SCSCF_SERVER_NAME, CxRequestFactory.SCSCF_HOST, CxRequestFactory.SCSCF_REALM);
            final var register = CxRequestFactory.serverAssignment(
                SEEDED_PRIVATE_IDENTITY, SEEDED_PUBLIC_IDENTITY, SERVER_ASSIGNMENT_REGISTRATION,
                UCN_IP_SM_GW_NAME, UCN_IP_SM_GW_REALM);
            assertThat(agent().sendAndWait(register, Duration.ofSeconds(5)).getResultCode())
                .isEqualTo(DiameterConstants.RES_DIAMETER_SUCCESS);

            final var deregister = CxRequestFactory.serverAssignment(SEEDED_PRIVATE_IDENTITY, SEEDED_PUBLIC_IDENTITY, SERVER_ASSIGNMENT_USER_DEREGISTRATION);

            final var saa = agent().sendAndWait(deregister, Duration.ofSeconds(5));

            assertThat(saa.getResultCode()).isEqualTo(DiameterConstants.RES_DIAMETER_SUCCESS);
            assertThat(SubscriberDb.ipSmGwName(SEEDED_IMSI)).as("IP-SM-GW after de-registration").isNull();
        }
    }

    @Nested
    class Rtr {
        @Test
        void marWithDifferentScscf_deregistersAtPreviousScscf() {
            // GIVEN a subscriber currently registered at a *different* S-CSCF
            SubscriberDb.setSimAuthKeys(SEEDED_IMSI, TEST_KI, TEST_OP, TEST_SQN);
            SubscriberDb.setScscf(SEEDED_IMSI, OLD_SCSCF_NAME, OLD_SCSCF_HOST, CxRequestFactory.SCSCF_REALM);

            // WHEN a MAR arrives from the agent's (new) S-CSCF
            final var maa = agent().sendAndWait(CxRequestFactory.multimediaAuth(SEEDED_PRIVATE_IDENTITY), Duration.ofSeconds(5));
            assertThat(maa.getResultCode()).isEqualTo(DiameterConstants.RES_DIAMETER_SUCCESS);

            // THEN the HSS deregisters the subscriber at the previous S-CSCF via an outbound RTR
            final var rtr = agent().awaitRequest(RegistrationTerminationRequest.In.class, Duration.ofSeconds(5));
            assertThat(rtr.getUserName()).isEqualTo(SEEDED_PRIVATE_IDENTITY);
            assertThat(rtr.getPublicIdentities()).containsExactly(SEEDED_PUBLIC_IDENTITY);

            final var reason = rtr.getDeregistrationReason();
            assertThat(reason).as("Deregistration-Reason").isNotNull();
            assertThat(reason.findAVP(new AVPKey(AVP_REASON_CODE, VENDOR_ID_3GPP)).getDataAsUnsignedInt())
                .isEqualTo(REASON_CODE_NEW_SERVER_ASSIGNED);

            // the stale registration was removed
            assertThat(SubscriberDb.scscf(SEEDED_IMSI)).as("S-CSCF after reassignment").isNull();
        }
    }

    private static void assertExperimentalResult(final AVPContainer experimentalResult, final long expectedCode) {
        assertThat(experimentalResult).as("Experimental-Result").isNotNull();
        assertThat(experimentalResult.findAVP(new AVPKey(DiameterConstants.AVP_VENDOR_ID, 0)).getDataAsUnsignedInt())
            .isEqualTo(VENDOR_ID_3GPP);
        assertThat(experimentalResult.findAVP(new AVPKey(DiameterConstants.AVP_EXPERIMENTAL_RESULT_CODE, 0)).getDataAsUnsignedInt())
            .isEqualTo(expectedCode);
    }

    private static RandomGenerator fixedRand(final byte[] rand) {
        return new RandomGenerator() {
            @Override public byte[] nextRand(final int size) { return rand; }
        };
    }
}
