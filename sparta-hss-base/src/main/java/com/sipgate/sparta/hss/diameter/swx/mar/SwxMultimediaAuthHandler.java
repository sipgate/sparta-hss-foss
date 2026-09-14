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
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.AVP_USER_NAME;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.RES_DIAMETER_MISSING_AVP;
import static jakarta.xml.bind.DatatypeConverter.parseHexBinary;

import com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants;
import com.sipgate.sparta.diameter._3gpp.swx.messages.MultimediaAuthAnswer;
import com.sipgate.sparta.diameter._3gpp.swx.messages.MultimediaAuthRequest;
import com.sipgate.sparta.diameter.base.core.DiameterConstants;
import com.sipgate.sparta.diameter.base.core.DiameterMessageFactory;
import com.sipgate.sparta.diameter.base.core.avp.AVP;
import com.sipgate.sparta.diameter.base.core.avp.AVPKey;
import com.sipgate.sparta.diameter.base.session.DiameterErrorAnswerException;
import com.sipgate.sparta.hss.diameter.client.RegisterableDiameterHandler;
import com.sipgate.sparta.hss.diameter.common.AnswerFactory;
import com.sipgate.sparta.hss.diameter.common.auth.AkaVector;
import com.sipgate.sparta.hss.diameter.common.auth.EapAkaPrimeKeyDerivation;
import com.sipgate.sparta.hss.diameter.common.auth.Authenticator;
import com.sipgate.sparta.hss.diameter.common.auth.MilenageInput;
import com.sipgate.sparta.hss.diameter.common.auth.MilenageLogger;
import com.sipgate.sparta.hss.diameter.common.auth.ResyncInfo;
import com.sipgate.sparta.hss.diameter.swx.sar.AaaServerAssignmentChanged;
import com.sipgate.sparta.hss.event.EventPublisher;
import com.sipgate.sparta.hss.persistence.LocationVowifiDao;
import com.sipgate.sparta.hss.persistence.SimDao;
import com.sipgate.sparta.hss.persistence.entities.LocationVowifi;
import jakarta.transaction.Transactional;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// SWx Multimedia-Auth-Request handler (3GPP TS 29.273 §8.1.2.1.2). The HSS acts as SWx server:
/// a 3GPP AAA Server sends MAR to fetch an AKA vector (EAP-AKA or EAP-AKA') for a non-3GPP
/// access user. On success the HSS answers MAA with the vector and stores the AAA-Server assignment.
///
/// Mirrors the Cx [com.sipgate.sparta.hss.diameter.cx.mar.MultimediaAuthHandler] structure
/// but uses SWx message types, EAP-AKA / EAP-AKA' schemes, and the LocationVowifi assignment logic.
@Transactional
public class SwxMultimediaAuthHandler implements RegisterableDiameterHandler<MultimediaAuthRequest.In, MultimediaAuthAnswer.Out> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SwxMultimediaAuthHandler.class);
    private static final int IMSI_LEN = 15;
    private static final Pattern RE_IMSI = Pattern.compile("^[0-9]{" + IMSI_LEN + "}$");
    private static final long AAA_FAILURE_INDICATION_ABSENT = -1L;

    // AMF is scheme-dependent (RFC 9048 §3.3 / TS 33.102: bit 0 = MSB of the first AMF octet is
    // the "AMF separation bit").
    //   EAP-AKA  — RFC 4187 imposes no separation-bit requirement → 0x0000 (TS 33.203 §6.1 style).
    //   EAP-AKA' — RFC 9048 §3.3 / TS 33.402 Annex A MANDATE the separation bit be set → 0x8000.
    // A conformant EAP-AKA' peer rejects an AUTN whose separation bit is 0.
    private static final byte[] AMF_EAP_AKA = parseHexBinary("0000");
    private static final byte[] AMF_EAP_AKA_PRIME = parseHexBinary("8000");

    private static final AVPKey KEY_SIP_AUTHENTICATION_SCHEME = new AVPKey(AVP_SIP_AUTHENTICATION_SCHEME, VENDOR_ID_3GPP);
    private static final AVPKey KEY_SIP_AUTHORIZATION = new AVPKey(AVP_SIP_AUTHORIZATION, VENDOR_ID_3GPP);
    private static final AVPKey KEY_SIP_AUTH_DATA_ITEM = new AVPKey(AVP_SIP_AUTH_DATA_ITEM, VENDOR_ID_3GPP);
    private static final AVPKey KEY_CONFIDENTIALITY_KEY = new AVPKey(AVP_CONFIDENTIALITY_KEY, VENDOR_ID_3GPP);
    private static final AVPKey KEY_INTEGRITY_KEY = new AVPKey(AVP_INTEGRITY_KEY, VENDOR_ID_3GPP);
    private static final AVPKey KEY_SIP_AUTHENTICATE = new AVPKey(AVP_SIP_AUTHENTICATE, VENDOR_ID_3GPP);
    private static final AVPKey KEY_SIP_ITEM_NUMBER = new AVPKey(AVP_SIP_ITEM_NUMBER, VENDOR_ID_3GPP);
    private static final AVPKey KEY_USER_NAME = new AVPKey(AVP_USER_NAME, 0);
    // Standard Diameter AVP (code 264, vendor 0)
    private static final AVPKey KEY_ORIGIN_HOST = new AVPKey(DiameterConstants.AVP_ORIGIN_HOST, 0);

    private final SimDao simDao;
    private final LocationVowifiDao locationVowifiDao;
    private final Authenticator authenticator;
    private final EapAkaPrimeKeyDerivation eapAkaPrimeKeyDerivation;
    private final EventPublisher eventPublisher;

    public SwxMultimediaAuthHandler(
        final SimDao simDao,
        final LocationVowifiDao locationVowifiDao,
        final Authenticator authenticator,
        final EapAkaPrimeKeyDerivation eapAkaPrimeKeyDerivation,
        final EventPublisher eventPublisher)
    {
        this.simDao = simDao;
        this.locationVowifiDao = locationVowifiDao;
        this.authenticator = authenticator;
        this.eapAkaPrimeKeyDerivation = eapAkaPrimeKeyDerivation;
        this.eventPublisher = eventPublisher;
    }


    @Override
    public Class<MultimediaAuthRequest.In> requestType() {
        return MultimediaAuthRequest.In.class;
    }

    @Override
    public CompletableFuture<MultimediaAuthAnswer.Out> handle(final MultimediaAuthRequest.In request) {
        final var userName = request.getUserName();
        if (userName == null) {
            return CompletableFuture.failedFuture(AnswerFactory.missingAvp(request, AVP.create(KEY_USER_NAME, "")));
        }
        if (userName.length() < IMSI_LEN) {
            return CompletableFuture.failedFuture(AnswerFactory.invalidAvpValue(request, request.findAVP(KEY_USER_NAME)));
        }
        if (!RE_IMSI.matcher(userName).matches()) {
            return CompletableFuture.failedFuture(AnswerFactory.invalidAvpValue(request, request.findAVP(KEY_USER_NAME)));
        }
        final var imsi = userName.substring(0, IMSI_LEN);
        try (final var milenageLogger = new MilenageLogger(imsi)) {
            return doHandle(milenageLogger, request, imsi);
        } catch (final Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private CompletableFuture<MultimediaAuthAnswer.Out> doHandle(
        final MilenageLogger milenageLogger, final MultimediaAuthRequest.In request, final String imsi)
        throws Exception
    {
        if (request.getSipAuthDataItem() == null) {
            LOGGER.warn("SWx MAR without SipAuthDataItem received - cannot process.");
            throw AnswerFactory.missingAvp(request, AVP.create(KEY_SIP_AUTH_DATA_ITEM, List.of()));
        }

        final var sipAuthSchemeAvp = request.getSipAuthDataItem().findAVP(KEY_SIP_AUTHENTICATION_SCHEME);
        if (sipAuthSchemeAvp == null) {
            throw AnswerFactory.missingAvp(request, AVP.create(KEY_SIP_AUTHENTICATION_SCHEME, ""));
        }
        final var scheme = sipAuthSchemeAvp.getDataAsString();
        // TS 29.273 §8.1.2.1.1, table 8.1.2.1.1/2: SIP-Authentication-Scheme is "EAP-AKA" or "EAP-AKA'".
        final var allowedSchemes = List.of("EAP-AKA", "EAP-AKA'");
        if (scheme == null || !allowedSchemes.contains(scheme)) {
            throw new DiameterErrorAnswerException(
                DiameterMessageFactory.createExperimentalResultAnswer(request, VENDOR_ID_3GPP, EXP_RES_DIAMETER_ERROR_AUTH_SCHEME_NOT_SUPPORTED));
        }

        // §8.1.2.1.2: Origin-Host carries the AAA Server identity requesting the vector.
        final var requestName = request.getOriginHost();
        if (requestName == null) {
            throw AnswerFactory.missingAvp(request, AVP.create(KEY_ORIGIN_HOST, ""));
        }

        // FOR-UPDATE lock: the vector generation below read-increments-writes the SIM's SQN, so
        // concurrent MARs for the same IMSI must serialise here or their SQNs overlap (TS 33.102).
        final var maybeSim = simDao.getSimByImsiStringForUpdate(imsi);
        if (maybeSim.isEmpty()) {
            throw new DiameterErrorAnswerException(DiameterMessageFactory.createExperimentalResultAnswer(
                request, VENDOR_ID_3GPP, EXP_RES_DIAMETER_ERROR_USER_UNKNOWN));
        }
        final var sim = maybeSim.get();

        final var stored = locationVowifiDao.getAaaServer(imsi);
        // absent → step 8 stores
        final var conflictAnswer = stored
            .map(locationVowifi -> checkAaaServerConflict(request, imsi, requestName, locationVowifi))
            .orElse(null);
        if (conflictAnswer != null) {
            return CompletableFuture.completedFuture(conflictAnswer);
        }

        // RFC 9048 §3.3 / TS 33.402 Annex A: EAP-AKA' binds CK'/IK' to the access-network
        // identity (ANID = network name). Without it the KDF cannot run.
        if ("EAP-AKA'".equals(scheme) && request.getAnid() == null) {
            throw new DiameterErrorAnswerException(DiameterMessageFactory.createErrorAnswer(
                request, RES_DIAMETER_MISSING_AVP));
        }

        final var sipAuthorizationAvp = request.getSipAuthDataItem().findAVP(KEY_SIP_AUTHORIZATION);
        final var sipAuthorizationBytes = sipAuthorizationAvp == null ? null : sipAuthorizationAvp.getData();

        LOGGER.info("imsi={} is trying to establish vowifi/AAA with ANID={} and scheme={}", imsi, request.getAnid(), scheme);

        // AMF is scheme-dependent: EAP-AKA' MANDATES the AMF separation bit (RFC 9048 §3.3);
        // plain EAP-AKA imposes no separation-bit requirement (RFC 4187), so 0x0000 is valid.
        final var amf = "EAP-AKA'".equals(scheme) ? AMF_EAP_AKA_PRIME : AMF_EAP_AKA;

        try {
            final var resyncInfo = ResyncInfo.fromConcatenated(sipAuthorizationBytes);
            final var v = authenticator.generate3GAuthenticationVector(milenageLogger, new MilenageInput(amf, resyncInfo, sim));
            final var aka = new AkaVector(v.rand(), v.autn(), v.xres(), v.confidentialityKey(), v.integrityKey(), Arrays.copyOfRange(v.autn(), 0, 6));

            final byte[] ck;
            final byte[] ik;
            if ("EAP-AKA'".equals(scheme)) {
                final var prime = eapAkaPrimeKeyDerivation.derive(aka.ck(), aka.ik(), request.getAnid(), aka.sqnXorAk());
                ck = prime.ckPrime();
                ik = prime.ikPrime();
            } else {
                ck = aka.ck();
                ik = aka.ik();
            }

            final var rand = aka.rand();
            final var autn = aka.autn();
            // TS 29.273 §8.1.2.1.1, table 8.1.2.1.1/5: SIP-Authenticate = RAND || AUTN (binary, RAND first).
            final var sipAuthenticate = new byte[rand.length + autn.length];
            System.arraycopy(rand, 0, sipAuthenticate, 0, rand.length);
            System.arraycopy(autn, 0, sipAuthenticate, rand.length, autn.length);

            final var answer = DiameterMessageFactory.createAnswer(request, DiameterConstants.RES_DIAMETER_SUCCESS);
            answer.addSipAuthDataItem(List.of(
                AVP.create(KEY_CONFIDENTIALITY_KEY, ck),
                AVP.create(KEY_INTEGRITY_KEY, ik),
                AVP.create(KEY_SIP_AUTHORIZATION, aka.xres()),
                AVP.create(KEY_SIP_AUTHENTICATE, sipAuthenticate),
                AVP.create(KEY_SIP_AUTHENTICATION_SCHEME, scheme),
                AVP.create(KEY_SIP_ITEM_NUMBER, 0L)
            ));
            answer.setSipNumberAuthItems(1);
            answer.setUserName(request.getUserName());

            // Step 8 — store the AAA-Server assignment only when it was previously absent.
            if (stored.isEmpty()) {
                LOGGER.info("imsi={} Inserting originHost/originRealm in locationVowifi DAO", imsi);
                locationVowifiDao.store(imsi, requestName, request.getOriginHost(), request.getOriginRealm());
                eventPublisher.publish(AaaServerAssignmentChanged.ofRegister(imsi, requestName));
            }

            return CompletableFuture.completedFuture(answer);
        } catch (final Exception _) {
            throw new DiameterErrorAnswerException(DiameterMessageFactory.createExperimentalResultAnswer(request, VENDOR_ID_3GPP,
                CxDxConstants.EXP_RES_DIAMETER_ERROR_SERVING_NODE_FEATURE_UNSUPPORTED));
        }

    }

    /// §8.1.2.1.2 step 7 — conflict check against the stored AAA-Server assignment.
    /// Returns an answer if the conflict must abort the MAR (IDENTITY_ALREADY_REGISTERED),
    /// or `null` to continue (no conflict, same-name re-auth, or override with AAA-Failure-Indication).
    private MultimediaAuthAnswer.Out checkAaaServerConflict(
        final MultimediaAuthRequest.In request, final String imsi, final String requestName,
        final LocationVowifi stored)
    {
        final var storedName = stored.getAaaServerName();
        if (storedName.equals(requestName)) {
            return null; // same name → no-op
        }

        // Conflict: stored name != request Origin-Host
        if (request.getAaaFailureIndication() == AAA_FAILURE_INDICATION_ABSENT) {
            LOGGER.info("imsi={} SWx MAR conflict, no AAA-Failure-Indication — responding IDENTITY_ALREADY_REGISTERED", imsi);
            final var answer = DiameterMessageFactory.createExperimentalResultAnswer(
                request, VENDOR_ID_3GPP, EXP_RES_DIAMETER_ERROR_IDENTITY_ALREADY_REGISTERED);
            answer.set3gppAaaServerName(storedName);
            answer.setUserName(request.getUserName());
            return answer;
        }

        // Override: AAA-Failure-Indication present → overwrite stored, continue
        LOGGER.info("imsi={} SWx MAR conflict with AAA-Failure-Indication — overwriting AAA-Server", imsi);
        locationVowifiDao.store(imsi, requestName, request.getOriginHost(), request.getOriginRealm());
        eventPublisher.publish(AaaServerAssignmentChanged.ofRegister(imsi, requestName));
        return null; // fall through to vector generation
    }
}
