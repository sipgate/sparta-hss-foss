package com.sipgate.sparta.hss.diameter.common.auth;

import io.micrometer.core.instrument.MeterRegistry;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * UMTS AKA authentication (3GPP TS 33.102) with the Milenage algorithm set f1, f1*, f2, f3, f4, f5, f5*
 * (3GPP TS 35.206). The operator key comes from the SIM entity, interpreted as OP or as the
 * pre-computed OPc according to the configured StoredKeyFormat; AMF is caller-supplied (scheme-dependent:
 * IMS-AKA/EAP-AKA = 0x0000, EAP-AKA'/EPS-AKA = 0x8000). Vectors are produced per §6.3.3 (AUTN)
 * and §6.3.5 (re-synchronisation). EPS-AKA KASME (3GPP TS 33.401 Annex A.2) is derived
 * separately when a visited PLMN is supplied.
 */
public class MilenageAuthenticator implements Authenticator {

    private static final int RESYNC_SAFETY_MARGIN = 10;
    private static final Logger LOGGER = LoggerFactory.getLogger(MilenageAuthenticator.class);

    private final RandomGenerator randGen;
    private final MeterRegistry meterRegistry;
    private final StoredKeyFormat storedKeyFormat;

    public MilenageAuthenticator(
            final RandomGenerator randGen,
            final MeterRegistry meterRegistry,
            final StoredKeyFormat storedKeyFormat) {
        this.randGen = randGen;
        this.meterRegistry = meterRegistry;
        this.storedKeyFormat = storedKeyFormat;
    }

    /// The SIM's operator-key column holds OP or the pre-computed OPc, depending on the
    /// deployment's [StoredKeyFormat].
    private byte[] resolveOpc(final byte[] secretKey, final byte[] storedOperatorKey) {
        return switch (storedKeyFormat) {
            case OP -> Milenage.computeOpC(secretKey, storedOperatorKey);
            case OPC -> storedOperatorKey;
        };
    }

    /**
     * Re-synchronisation (3GPP TS 33.102 §6.3.5). AUTS is 14 bytes = SQN_MS⊕AK* (bytes 0..5)
     * ‖ MAC-S (bytes 6..13). Recover SQN_MS = AUTS[0..5] ⊕ AK*, where AK* = Milenage f5*
     * (TS 35.206); recompute MAC-S = Milenage f1* with AMF = 0x0000 (§6.3.5) and compare
     * constant-time. On match, adopt SQN_MS (+ safety margin) as the new SQN.
     *
     * @return true if the computed MAC-S matches the received MAC-S; false otherwise
     */
    boolean resyncSQN(final MilenageLogger milenageLogger, final MilenageInput input) {
        meterRegistry.counter("milenage_resync_sqn_attempt").increment();
        try {
            final var sim = input.sim();
            final var resyncInfo = input.resyncInfo();
            final var rand = resyncInfo.rand();
            final var auts = resyncInfo.auts();
            final var secretKey = sim.getSecretKey();
            final var op_c = resolveOpc(secretKey, sim.getOp());
            final var mac_s_RECEIVED = new byte[8];

            // AUTS is 14 bytes (112 bits): SQN_MS⊕AK* (6) ‖ MAC-S (8) (TS 33.102 §6.3.5).
            final var sqnMs = new byte[6];

            final var ak = Milenage.f5star(secretKey, rand, op_c); // AK* = f5* (TS 35.206)

            for (var i = 0; i < 6; i++) {
                sqnMs[i] = (byte) (ak[i] ^ auts[i]); // recover SQN_MS = AUTS[0..5] ⊕ AK*
            }

            System.arraycopy(auts, 6, mac_s_RECEIVED, 0, 8); // MAC-S = AUTS[6..13]

            milenageLogger.log("[MA75] using AMF: {}", input.amf());
            final var mac_s_COMPUTED = Milenage.f1star(secretKey, rand, op_c, sqnMs, input.amf()); // MAC-S = f1* (TS 35.206)

            // Constant-time MAC comparison (java.security.MessageDigest.isEqual) to avoid a
            // timing oracle on the MAC-S verification in the re-synchronisation path (TS 33.102 §6.3.5).
            if (MessageDigest.isEqual(mac_s_COMPUTED, mac_s_RECEIVED)) {
                //Daniel: This seems to be wrong here.
                //
                //if (concealSqn)
                //	sqnMs = concealSQN(secretKey, rand, op_c, sqnMs);

                // no need to step backwards with imsi, so use max..
                final var sqn = input.updateSimSqn(
                        milenageLogger,
                        n -> Math.max(convertSqnByteArrayToLong(sqnMs) + RESYNC_SAFETY_MARGIN, n)
                );
                LOGGER.debug("[{}] new synced sqn: {}",
                        sim.getFirstImsiObject(),
                        sqn);
                meterRegistry.counter("milenage_resync_sqn_success").increment();
                return true;
            }

            meterRegistry.counter("milenage_resync_sqn_invalid_mac").increment();
            LOGGER.debug("[{}] resync fail!", sim.getFirstImsiObject());
            return false;
        } catch (final Exception ex) {
            meterRegistry.counter("milenage_resync_sqn_exception", "exception", ex.getClass().getSimpleName()).increment();
            LOGGER.error(ex.getMessage(), ex);
            return false;
        }
    }

    private static byte[] concealSQN(final byte[] secretKey, final byte[] rand, final byte[] op_c, final byte[] sqn) {
        final var concealed = new byte[sqn.length];
        final var ak = Milenage.f5(secretKey, rand, op_c);
        for (var i = 0; i < 6; i++) {
            concealed[i] = (byte) (sqn[i] ^ ak[i]);
        }
        return concealed;
    }

    @Override
    public AkaV1Md5 generate4GAuthenticationVector(final MilenageLogger milenageLogger, final MilenageInput input, final String visitedPlmnId)
            throws Exception {
        meterRegistry.counter("milenage_generate_vector", "technology", "4g").increment();
        return generateAkaV1Md5(milenageLogger, input, visitedPlmnId);
    }

    @Override
    public AkaV1Md5 generate3GAuthenticationVector(final MilenageLogger milenageLogger, final MilenageInput input)
            throws Exception {
        meterRegistry.counter("milenage_generate_vector", "technology", "3g").increment();
        return generateAkaV1Md5(milenageLogger, input, null);
    }

    private AkaV1Md5 generateAkaV1Md5(final MilenageLogger milenageLogger, final MilenageInput input, final String visitedPlmnId) throws Exception {
        meterRegistry.counter("milenage_generate_aka").increment();
        final var resyncInfo = input.resyncInfo();
        final var sim = input.sim();

        input.updateSimSqn(milenageLogger, sqn -> {
            milenageLogger.log("[MA136] increment with minimal SQN value and validate not null");
            return Math.max(32, sqn == null ? 0L : sqn) + 2;
        });

        if (resyncInfo != null) {
            // TS 33.102 §6.3.5: MAC-S is computed with AMF = 0x0000 (the AMF/separation bits of
            // the original vector are not used in re-synchronisation).
            milenageLogger.log("[MA155] calculating new SQN");
            if (resyncSQN(milenageLogger, new MilenageInput(new byte[]{0x00, 0x00}, resyncInfo, sim))) {
                milenageLogger.log("[MA162] calculation successful: new SQN: {}", sim.getSqn());
            } else {
                milenageLogger.log("[MA164] calculation failed");
                throw new Exception("resync of sqn failed!");
            }

        }

        LOGGER.debug("using sqn {} to create new AkaV1Md5", sim.getSqn());
        final var sqn = convertSqnLongToByteArray(sim.getSqn());

        if (sqn.length != 6) {
            throw new Exception("sqn is invalid");
        }
        if (sim.getSecretKey() == null || sim.getSecretKey().length < 15) {
            throw new Exception("imsi key is missing");
        }

        milenageLogger.log("[MA183] using AMF: {} - using PLMNid: '{}'", input.amf(), visitedPlmnId);

        final var secretKey = sim.getSecretKey();
        final var op_c = resolveOpc(secretKey, sim.getOp());
        final var rand = randGen.nextRand(16);
        final var mac_a = Milenage.f1(secretKey, rand, op_c, sqn, input.amf()); // MAC-A = f1 (TS 35.206)
        final var xres = Milenage.f2(secretKey, rand, op_c); // RES = f2
        final var ck = Milenage.f3(secretKey, rand, op_c); // CK = f3
        final var ik = Milenage.f4(secretKey, rand, op_c); // IK = f4
        final var ak = Milenage.f5(secretKey, rand, op_c); // AK = f5 (conceals SQN in AUTN)
        final var autn = generateAutn(sqn, input.amf(), mac_a, secretKey, rand, op_c);
        final var kasme = generateKasme(ik, ck, sqn, ak, visitedPlmnId);

        return new AkaV1Md5(rand, autn, xres, kasme, ck, ik);
    }

    /**
     * Builds AUTN = SQN⊕AK ‖ AMF ‖ MAC-A (3GPP TS 33.102 §6.3.3), 16 bytes total:
     *   bytes 0..5  SQN⊕AK  (SQN concealed with the anonymity key AK = Milenage f5, TS 35.206)
     *   bytes 6..7  AMF      (authentication management field, placed verbatim)
     *   bytes 8..15 MAC-A    (network authentication code, Milenage f1, TS 35.206)
     *
     * @param sqn   sequence number
     * @param amf   authentication management field
     * @param mac_a network authentication code MAC-A
     * @return AUTN
     */
    private static byte[] generateAutn(final byte[] sqn, final byte[] amf,
                                       final byte[] mac_a,
                                       final byte[] secretKey, final byte[] rand, final byte[] op_c) {
        final var concealedSQN = concealSQN(secretKey, rand, op_c, sqn); // SQN⊕AK
        final var result = new byte[16];
        // AUTN = SQN⊕AK[0..5] ‖ AMF[6..7] ‖ MAC-A[8..15] (TS 33.102 §6.3.3)
        System.arraycopy(concealedSQN, 0, result, 0, 6);
        System.arraycopy(amf, 0, result, 6, 2);
        System.arraycopy(mac_a, 0, result, 8, 8);
        return result;
    }

    /**
     * EPS-AKA KASME derivation (3GPP TS 33.401 Annex A.2, using the TS 33.220 §B.2 KDF):
     *   KEY = CK ‖ IK (32 bytes);
     *   S = FC(0x10) ‖ SN_id(3) ‖ L0(0x0003) ‖ SQN⊕AK(6) ‖ L1(0x0006);
     *   KASME = HMAC-SHA256(KEY, S). SN_id is the serving-network ID derived from MCC/MNC (TS 24.301).
     *   (Distinct from the EAP-AKA' CK'/IK' derivation, which uses FC=0x20 — see EapAkaPrimeKeyDerivation.)
     */
    private byte[] generateKasme(final byte[] ik, final byte[] ck, final byte[] sqn, final byte[] ak,
                                 final String visitedPlmnId) throws NoSuchAlgorithmException, InvalidKeyException {

        if (visitedPlmnId == null) {
            meterRegistry.counter("milenage_generate_kasme", "mcc", "(null)", "mnc", "(null)").increment();
            return null;
        }

        final var mcc = MncMccFormatter.plmnToMcc(visitedPlmnId);
        final var mnc = MncMccFormatter.plmnToMnc(visitedPlmnId);
        meterRegistry.counter("milenage_generate_kasme", "mcc", mcc, "mnc", mnc).increment();

        final var s = new byte[14];
        final var key = new byte[32];

        s[0] = 0x10; // FC = 0x10 (TS 33.401 Annex A.2)

        // convert mcc/mnc to sn_id (3 bytes) — P0 = serving-network ID
        final var sn_id = MncMccFormatter.mccMncToSnId(mcc, mnc);
        s[1] = sn_id[0];
        s[2] = sn_id[1];
        s[3] = sn_id[2];

        s[4] = 0x00; // L0 = length of P0 (SN_id) = 3 (TS 33.220 §B.2)
        s[5] = 0x03;
        for (var i = 0; i < 6; i++) {
            s[6 + i] = (byte) (sqn[i] ^ ak[i]); // P1 = SQN⊕AK
        }
        s[12] = 0x00; // L1 = length of P1 (SQN⊕AK) = 6 (TS 33.220 §B.2)
        s[13] = 0x06;

        // KEY = CK ‖ IK (TS 33.401 Annex A.2)
        for (var i = 0; i < 16; i++) {
            key[i] = ck[i];
            key[16 + i] = ik[i];
        }

        final var sha256_HMAC = Mac.getInstance("HmacSHA256");
        final var secretKeySpec = new SecretKeySpec(key, "HmacSHA256");
        sha256_HMAC.init(secretKeySpec);
        return sha256_HMAC.doFinal(s);
    }

    /**
     * Converts given SQN Value into an integer. This is NOT a general conversion method for byte arrays!
     *
     * @param sqnMs SQN Value as byte array. Must contain at least 4 elements, furthermore elements will be ignored
     * @return sqnMs as int
     * @throws Exception if sqn raw data is invalid
     */
    private static Long convertSqnByteArrayToLong(final byte[] sqnMs) throws Exception {
        if (sqnMs == null || sqnMs.length != 6) {
            throw new Exception("SQN raw data is invalid.");
        }
        long l = 0;

        for (var i = 0; i < 6; i++) {
            l <<= 8;
            l ^= (long) sqnMs[i] & 0xff;
        }
        return l;
    }

    /**
     * Converts given SQN value to byte array. This is NOT a general conversion method for byte arrays!
     *
     * @param sqn SQN Value as byte array. Must contain at least 4 elements, furthermore elements will be ignored
     * @return sqnMs as int
     */
    private static byte[] convertSqnLongToByteArray(final Long sqn) throws IOException {
        final var arrayOutputStream = new ByteArrayOutputStream(0);
        final var dataOutputStream = new DataOutputStream(arrayOutputStream);

        dataOutputStream.writeLong(sqn);
        dataOutputStream.flush();
        final var sqnArray = arrayOutputStream.toByteArray();
        for (var i = 0; i < (6 - sqnArray.length); i++) {
            sqnArray[i] = 0;
        }
        final var result = new byte[6];
        System.arraycopy(sqnArray, 2, result, 0, 6);
        return result;
    }
}
