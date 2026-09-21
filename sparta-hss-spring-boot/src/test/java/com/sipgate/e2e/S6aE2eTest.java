package com.sipgate.e2e;

import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.VENDOR_ID_3GPP;
import static jakarta.xml.bind.DatatypeConverter.parseHexBinary;
import static org.assertj.core.api.Assertions.assertThat;

import com.sipgate.e2e.requests.S6aRequestFactory;
import com.sipgate.e2e.utils.HssProfileApi;
import com.sipgate.e2e.utils.SubscriberDb;
import com.sipgate.sparta.diameter._3gpp.common._3gppConstants;
import com.sipgate.sparta.diameter._3gpp.rx.RxConstants;
import com.sipgate.sparta.diameter._3gpp.s6a.S6aConstants;
import com.sipgate.sparta.diameter._3gpp.s6a.messages.CancelLocationRequest;
import com.sipgate.sparta.diameter._3gpp.s6a.messages.InsertSubscriberDataRequest;
import com.sipgate.sparta.diameter.base.core.Answer;
import com.sipgate.sparta.diameter.base.core.avp.mixins.HasExperimentalResultAVP;
import com.sipgate.sparta.diameter.base.core.DiameterConstants;
import com.sipgate.sparta.diameter.base.core.avp.AVPContainer;
import com.sipgate.sparta.diameter.base.core.avp.AVPKey;
import com.sipgate.sparta.hss.diameter.common.Tbcd;
import com.sipgate.sparta.hss.diameter.common.auth.AkaV1Md5;
import com.sipgate.sparta.hss.diameter.common.auth.MilenageAuthenticator;
import com.sipgate.sparta.hss.diameter.common.auth.MilenageInput;
import com.sipgate.sparta.hss.diameter.common.auth.MilenageLogger;
import com.sipgate.sparta.hss.diameter.common.auth.RandomGenerator;
import com.sipgate.sparta.hss.diameter.common.auth.StoredKeyFormat;
import com.sipgate.sparta.hss.persistence.entities.Sim;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.HashMap;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class S6aE2eTest extends AbstractDiameterE2eTest {

    private static final String SEEDED_IMSI = "001010000000001";
    private static final String SEEDED_MSISDN = "4915790000001";
    private static final String UNKNOWN_IMSI = "001019999999999";

    /** Visited-PLMN-Id 00101 (MCC=001, MNC=01) in 3-octet encoding. */
    static final byte[] VISITED_PLMN_00101 = {0x00, 0x01, 0x10};

    private static final byte[] TEST_KI = parseHexBinary("000102030405060708090A0B0C0D0E0F");
    private static final byte[] TEST_OP = parseHexBinary("0F0E0D0C0B0A09080706050403020100");
    private static final long   TEST_SQN = 96L; // >= 32, deterministic
    private static final byte[] AMF_4G = parseHexBinary("8000");

    private static final String OLD_MME_HOST = "mmec99.mmegi8001.mme.epc.mnc001.mcc001.3gppnetwork.org";

    @Nested
    class Air {
        @Test
        void knownImsi_returnsVerifiableEutranVector() throws Exception {
            // GIVEN a subscriber with known Ki/OP/SQN so the vector is deterministic given the RAND
            SubscriberDb.setSimAuthKeys(SEEDED_IMSI, TEST_KI, TEST_OP, TEST_SQN);

            // WHEN one E-UTRAN vector is requested
            final var air = S6aRequestFactory.authenticationInformation(SEEDED_IMSI, VISITED_PLMN_00101, 1L);
            final var aia = agent().sendAndWait(air, Duration.ofSeconds(5));

            // THEN success with exactly one E-UTRAN vector
            assertThat(aia.getResultCode()).isEqualTo(DiameterConstants.RES_DIAMETER_SUCCESS);
            final var authInfo = aia.getAuthenticationInfo();
            assertThat(authInfo).as("Authentication-Info").isNotNull();
            final var vectors = authInfo.findAVPs(new AVPKey(S6aConstants.AVP_E_UTRAN_VECTOR, VENDOR_ID_3GPP));
            assertThat(vectors).hasSize(1);
            final var vector = (AVPContainer) vectors.getFirst();

            final var rand = octets(vector, S6aConstants.AVP_RAND);
            final var xres = octets(vector, S6aConstants.AVP_XRES);
            final var autn = octets(vector, S6aConstants.AVP_AUTN);
            final var kasme = octets(vector, S6aConstants.AVP_KASME);
            assertThat(rand).hasSize(16);
            assertThat(autn).hasSize(16);
            assertThat(kasme).hasSize(32);
            assertThat(vector.findAVP(new AVPKey(S6aConstants.AVP_ITEM_NUMBER, VENDOR_ID_3GPP)).getDataAsUnsignedInt())
                .isEqualTo(1L);

            // recompute the whole vector from the returned RAND + the keys/SQN we set -> must match
            final var sim = new Sim();
            sim.setSecretKey(TEST_KI);
            sim.setOp(TEST_OP);
            sim.setSqn(TEST_SQN);
            final var authenticator = new MilenageAuthenticator(fixedRand(rand), new SimpleMeterRegistry(), StoredKeyFormat.OP);
            final AkaV1Md5 expected;
            try (final var logger = new MilenageLogger(SEEDED_IMSI)) {
                expected = authenticator.generate4GAuthenticationVector(
                    logger, new MilenageInput(AMF_4G, null, sim), Tbcd.decodePlmnId(VISITED_PLMN_00101));
            }
            assertThat(rand).as("RAND fed back into the oracle").isEqualTo(expected.rand());
            assertThat(xres).as("XRES").isEqualTo(expected.xres());
            assertThat(autn).as("AUTN").isEqualTo(expected.autn());
            assertThat(kasme).as("KASME").isEqualTo(expected.kasme());
        }

        @Test
        void unknownImsi_returnsUserUnknown() {
            final var aia = agent().sendAndWait(
                S6aRequestFactory.authenticationInformation(UNKNOWN_IMSI, VISITED_PLMN_00101, 1L), Duration.ofSeconds(5));

            assertExperimentalResultAnswer(aia, _3gppConstants.EXP_RES_DIAMETER_ERROR_USER_UNKNOWN);
        }
    }

    @Nested
    class Ulr {
        @Test
        void knownImsi_returnsSuccessWithSubscriptionData() {
            //GIVEN
            final var ulr = S6aRequestFactory.updateLocation(SEEDED_IMSI, VISITED_PLMN_00101);

            //WHEN
            final var ula = agent().sendAndWait(ulr, Duration.ofSeconds(5));

            //THEN
            assertThat(ula.getResultCode()).isEqualTo(DiameterConstants.RES_DIAMETER_SUCCESS);

            final var subscriptionData = ula.getSubscriptionData();
            assertThat(subscriptionData).as("Subscription-Data").isNotNull();
            // right subscriber resolved: MSISDN matches the seeded number (TBCD-encoded)
            assertThat(subscriptionData.findAVP(new AVPKey(_3gppConstants.AVP_MSISDN, VENDOR_ID_3GPP)).getDataAsOctetString())
                .isEqualTo(Tbcd.encodeTbcd(SEEDED_MSISDN));
            // profile applied: Subscriber-Status = serviceGranted (0)
            assertThat(subscriptionData.findAVP(new AVPKey(S6aConstants.AVP_SUBSCRIBER_STATUS, VENDOR_ID_3GPP)).getDataAsUnsignedInt())
                .isEqualTo(0L);
            // nested grouped AVPs survived the wire: AMBR carries the profile bandwidths
            final var ambr = (AVPContainer) subscriptionData.findAVP(new AVPKey(S6aConstants.AVP_AMBR, VENDOR_ID_3GPP));
            assertThat(ambr.findAVP(new AVPKey(RxConstants.AVP_MAX_REQUESTED_BANDWIDTH_UL, VENDOR_ID_3GPP)).getDataAsUnsignedInt())
                .isEqualTo(32_000_000L);
            assertThat(ambr.findAVP(new AVPKey(RxConstants.AVP_MAX_REQUESTED_BANDWIDTH_DL, VENDOR_ID_3GPP)).getDataAsUnsignedInt())
                .isEqualTo(50_000_000L);
            // the APN-Configuration-Profile grouped AVP round-tripped
            assertThat(subscriptionData.findAVP(new AVPKey(S6aConstants.AVP_APN_CONFIGURATION_PROFILE, VENDOR_ID_3GPP)))
                .as("APN-Configuration-Profile").isNotNull();
        }

        @Test
        void unknownImsi_returnsUserUnknown() {
            final var ula = agent().sendAndWait(
                S6aRequestFactory.updateLocation(UNKNOWN_IMSI, VISITED_PLMN_00101), Duration.ofSeconds(5));

            assertExperimentalResultAnswer(ula, _3gppConstants.EXP_RES_DIAMETER_ERROR_USER_UNKNOWN);
        }

        @Test
        void fromNewMme_triggersCancelLocationToOldMme() {
            SubscriberDb.setLteLocation(SEEDED_IMSI, OLD_MME_HOST, S6aRequestFactory.MME_REALM, "00101");

            final var ula = agent().sendAndWait(S6aRequestFactory.updateLocation(SEEDED_IMSI, VISITED_PLMN_00101), Duration.ofSeconds(5));
            assertThat(ula.getResultCode()).isEqualTo(DiameterConstants.RES_DIAMETER_SUCCESS);

            final var clr = agent().awaitRequest(CancelLocationRequest.In.class, Duration.ofSeconds(5));
            assertThat(clr.getUserName()).isEqualTo(SEEDED_IMSI);
            assertThat(clr.findAVP(new AVPKey(S6aConstants.AVP_CANCELLATION_TYPE, VENDOR_ID_3GPP)).getDataAsUnsignedInt())
                .isEqualTo(S6aConstants.CANCELLATION_TYPE_MME_UPDATE_PROCEDURE);

            // the old registration was replaced by the new MME
            assertThat(SubscriberDb.lteLocationMmeHostname(SEEDED_IMSI)).isEqualTo(S6aRequestFactory.MME_HOST);
        }
    }

    @Nested
    class Pur {
        @Test
        void pur_removesTheStoredLocation() {
            SubscriberDb.setLteLocation(SEEDED_IMSI, S6aRequestFactory.MME_HOST, S6aRequestFactory.MME_REALM, "00101");
            final var pua = agent().sendAndWait(S6aRequestFactory.purgeUe(SEEDED_IMSI), Duration.ofSeconds(5));
            assertThat(pua.getResultCode()).isEqualTo(DiameterConstants.RES_DIAMETER_SUCCESS);
            assertThat(SubscriberDb.lteLocationMmeHostname(SEEDED_IMSI)).as("location after purge").isNull();
        }
    }

    @Nested
    class Nor {
        @Test
        void nor_knownImsi_returnsSuccess() {
            final var nor = S6aRequestFactory.notifyRequest(SEEDED_IMSI);
            final var noa = agent().sendAndWait(nor, Duration.ofSeconds(5));
            assertThat(noa.getResultCode()).isEqualTo(DiameterConstants.RES_DIAMETER_SUCCESS);
        }
    }

    @Nested
    class Isd {
        // A dedicated subscriber: the batch API changes its profile persistently, so keeping it off
        // SEEDED_IMSI avoids disturbing the ULR/AIR assertions on the default profile.
        private static final String ISD_IMSI = "001010000000002";
        private static final String ISD_MSISDN = "4915790000002";

        @Test
        void profileChange_pushesInsertSubscriberDataToServingMme() {
            // GIVEN the subscriber is registered at an MME and carries the default profile
            SubscriberDb.clearImsiProfile(ISD_IMSI);
            SubscriberDb.setLteLocation(ISD_IMSI, S6aRequestFactory.MME_HOST, S6aRequestFactory.MME_REALM, "00101");

            // WHEN its profile changes (POST the full desired state so the seeded rows are preserved)
            final var desired = new HashMap<>(SubscriberDb.allImsiProfiles());
            desired.put(ISD_IMSI, "no-volte");
            final var status = HssProfileApi.updateImsiProfiles(desired);
            assertThat(status).as("POST /profile/imsi/batch status").isEqualTo(204);

            // THEN the HSS pushes the new subscription data to the serving MME
            final var isd = agent().awaitRequest(InsertSubscriberDataRequest.In.class, Duration.ofSeconds(15));
            assertThat(isd.getUserName()).isEqualTo(ISD_IMSI);

            final var subscriptionData = isd.getSubscriptionData();
            assertThat(subscriptionData).as("Subscription-Data").isNotNull();
            assertThat(subscriptionData.findAVP(new AVPKey(_3gppConstants.AVP_MSISDN, VENDOR_ID_3GPP)).getDataAsOctetString())
                .isEqualTo(Tbcd.encodeTbcd(ISD_MSISDN));
        }
    }

    private static void assertExperimentalResult(final AVPContainer experimentalResult, final long expectedCode) {
        assertThat(experimentalResult).as("Experimental-Result").isNotNull();
        assertThat(experimentalResult.findAVP(new AVPKey(DiameterConstants.AVP_VENDOR_ID, 0)).getDataAsUnsignedInt())
            .isEqualTo(VENDOR_ID_3GPP);
        assertThat(experimentalResult.findAVP(new AVPKey(DiameterConstants.AVP_EXPERIMENTAL_RESULT_CODE, 0)).getDataAsUnsignedInt())
            .isEqualTo(expectedCode);
    }

    /**
     * A 3GPP Experimental-Result travels in a regular answer, not in an E-bit error answer
     * (RFC 6733 §7.6), and that answer must be complete: the MME needs Auth-Session-State and
     * Vendor-Specific-Application-Id back, which only the S6a message factory fills in.
     */
    private static <A extends Answer & HasExperimentalResultAVP> void assertExperimentalResultAnswer(
        final A answer, final long expectedCode)
    {
        assertThat(answer.isError()).as("E-bit must stay off for an Experimental-Result").isFalse();
        assertThat(answer.getResultCode()).as("no base Result-Code next to an Experimental-Result").isEqualTo(-1L);
        assertExperimentalResult(answer.getExperimentalResult(), expectedCode);
        assertThat(answer.findAVP(new AVPKey(DiameterConstants.AVP_AUTH_SESSION_STATE, 0)).getDataAsInt())
            .as("Auth-Session-State").isEqualTo(DiameterConstants.AUTH_SESSION_STATE_NOT_MAINTAINED);
        final var vsai = (AVPContainer) answer.findAVP(new AVPKey(DiameterConstants.AVP_VENDOR_SPECIFIC_APPLICATION_ID, 0));
        assertThat(vsai).as("Vendor-Specific-Application-Id").isNotNull();
        assertThat(vsai.findAVP(new AVPKey(DiameterConstants.AVP_AUTH_APPLICATION_ID, 0)).getDataAsUnsignedInt())
            .isEqualTo(S6aConstants.APP_ID_S6A_S6D);
    }

    private static byte[] octets(final AVPContainer vector, final int avpCode) {
        return vector.findAVP(new AVPKey(avpCode, VENDOR_ID_3GPP)).getDataAsOctetString();
    }

    private static RandomGenerator fixedRand(final byte[] rand) {
        return new RandomGenerator() {
            @Override public byte[] nextRand(final int size) { return rand; }
        };
    }
}
