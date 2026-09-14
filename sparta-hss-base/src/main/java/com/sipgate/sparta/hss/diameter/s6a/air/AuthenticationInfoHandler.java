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
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.RES_DIAMETER_INVALID_AVP_VALUE;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.RES_DIAMETER_SUCCESS;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.RES_DIAMETER_UNABLE_TO_COMPLY;

import com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants;
import com.sipgate.sparta.diameter._3gpp.s6a.messages.AuthenticationInformationAnswer;
import com.sipgate.sparta.diameter._3gpp.s6a.messages.AuthenticationInformationRequest;
import com.sipgate.sparta.diameter.base.core.DiameterMessageFactory;
import com.sipgate.sparta.diameter.base.core.avp.AVP;
import com.sipgate.sparta.diameter.base.core.avp.AVPContainer;
import com.sipgate.sparta.diameter.base.core.avp.AVPKey;
import com.sipgate.sparta.diameter.base.session.DiameterErrorAnswerException;
import com.sipgate.sparta.hss.diameter.client.RegisterableDiameterHandler;
import com.sipgate.sparta.hss.diameter.common.AnswerFactory;
import com.sipgate.sparta.hss.diameter.common.Tbcd;
import com.sipgate.sparta.hss.diameter.common.auth.Authenticator;
import com.sipgate.sparta.hss.diameter.common.auth.MilenageInput;
import com.sipgate.sparta.hss.diameter.common.auth.MilenageLogger;
import com.sipgate.sparta.hss.diameter.common.auth.ResyncInfo;
import com.sipgate.sparta.hss.persistence.SimDao;
import com.sipgate.sparta.hss.persistence.entities.Sim;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import jakarta.xml.bind.DatatypeConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthenticationInfoHandler
        implements RegisterableDiameterHandler<AuthenticationInformationRequest.In, AuthenticationInformationAnswer.Out> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationInfoHandler.class);

    // Caching is always the problem. (And DNS)
    // We assume, that our vectors are cached by the MME/SGSN
    // which leads to SQN being out of sync after 2G, 4G and IMS authentication.
    static final int MAX_NUMBER_OF_VECTORS = 1;
    // 3GPP TS 33.401 version 17.3.0 Release 17 V17.3.0 (2022-09)
    // 6.1.1 AKA procedure
    // An ME accessing E-UTRAN shall check during authentication that the "separation bit" in the AMF field of AUTN is
    // set to 1. The "separation bit" is bit 0 of the AMF field of AUTN
    private static final byte[] AMF_4G = DatatypeConverter.parseHexBinary("8000");

    // At the time of writing, we could not find any specification mentioning AMF (or separation bit) for 3G
    // => set AMF to 0000
    private static final byte[] AMF_3G = DatatypeConverter.parseHexBinary("0000");

    // There is no AMF_2G because 2G does not use AUTN and AUTN is the only thing that uses AMF

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

    private final SimDao simDao;
    private final EutranAccessPolicy eutranAccessPolicy;
    private final Authenticator authenticator;

    public AuthenticationInfoHandler(
        final SimDao simDao,
        final EutranAccessPolicy eutranAccessPolicy,
        final Authenticator authenticator)
    {
        this.simDao = simDao;
        this.eutranAccessPolicy = eutranAccessPolicy;
        this.authenticator = authenticator;
    }

    @Override
    public Class<AuthenticationInformationRequest.In> requestType() {
        return AuthenticationInformationRequest.In.class;
    }

    @Override
    @Transactional
    public CompletableFuture<AuthenticationInformationAnswer.Out> handle(final AuthenticationInformationRequest.In request) {
        final var imsi = request.getUserName();
        try (final var resyncLogger = new MilenageLogger(imsi)) {
            return doHandle(resyncLogger, request);
        } catch (final Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private CompletableFuture<AuthenticationInformationAnswer.Out> doHandle(final MilenageLogger milenageLogger, final AuthenticationInformationRequest.In request) throws DiameterErrorAnswerException {
        final var imsi = request.getUserName();
        final var sim = simDao.getSimByImsiString(imsi);

        if (sim.isEmpty()) {
            LOGGER.warn("unable to query sim for imsi {}", imsi);
            throw new DiameterErrorAnswerException(DiameterMessageFactory.createExperimentalResultAnswer(request, VENDOR_ID_3GPP, EXP_RES_DIAMETER_ERROR_USER_UNKNOWN));
        }

        final var eutranAuthInfo = request.getRequestedEutranAuthenticationInfo();
        final var utranGeranAuthInfo = request.getRequestedUtranGeranAuthenticationInfo();
        if (needsReSynchronisationForEutranAndUtranGeran(eutranAuthInfo, utranGeranAuthInfo)) {
            throw new DiameterErrorAnswerException(DiameterMessageFactory.createErrorAnswer(request, RES_DIAMETER_UNABLE_TO_COMPLY));
        }

        final var visitedPlmnId = Tbcd.decodePlmnId(request.getVisitedPlmnId());
        if (!isAllowedFor4G(imsi, visitedPlmnId)) {
            LOGGER.info("imsi {} is not allowed to use 4g data", imsi);
            throw new DiameterErrorAnswerException(DiameterMessageFactory.createExperimentalResultAnswer(request, VENDOR_ID_3GPP, EXP_RES_DIAMETER_ERROR_UNKNOWN_EPS_SUBSCRIPTION));
        }

        final var authenticationInfo = process4GAuthentication(request, milenageLogger, eutranAuthInfo, utranGeranAuthInfo, visitedPlmnId, imsi, sim.get());

        final var authAnswer = DiameterMessageFactory.createAnswer(request, RES_DIAMETER_SUCCESS);
        authAnswer.setAuthenticationInfo(authenticationInfo);
        // never log the answer itself: it carries authentication vectors (key material)
        LOGGER.info("AuthInfo success for imsi {}", imsi);
        return CompletableFuture.completedFuture(authAnswer);
    }

    private static boolean needsReSynchronisationForEutranAndUtranGeran(final AVPContainer eutranAuthInfo, final AVPContainer utranGeranAuthInfo) {
        return eutranAuthInfo != null
           && utranGeranAuthInfo != null
           && eutranAuthInfo.findAVP(KEY_RE_SYNCHRONIZATION_INFO) != null
           && utranGeranAuthInfo.findAVP(KEY_RE_SYNCHRONIZATION_INFO) != null;
    }

    private boolean isAllowedFor4G(final String imsi, final String visitedPlmnId) {
        if (visitedPlmnId != null && visitedPlmnId.length() > 2) {
            return eutranAccessPolicy.isEutranAccessAllowed(imsi, visitedPlmnId.substring(0, 3));
        }

        return false;
    }

    private List<AVP> process4GAuthentication(
        final AuthenticationInformationRequest.In request, final MilenageLogger milenageLogger,
        final AVPContainer eutranAuthInfo,
        final AVPContainer utranGeranAuthInfo,
        final String visitedPlmnId,
        final String imsi,
        final Sim sim) throws DiameterErrorAnswerException {
        LOGGER.debug("{}: authentication with 4G", imsi);

        final List<AVP> authenticationInfo = new ArrayList<>();
        authenticationInfo.addAll(buildEutranVectors(request, milenageLogger, eutranAuthInfo, visitedPlmnId, imsi, sim));
        authenticationInfo.addAll(buildUtranVectors(request, milenageLogger, utranGeranAuthInfo, imsi, sim));

        return authenticationInfo;
    }

    private List<AVP> buildEutranVectors(
        final AuthenticationInformationRequest.In request, final MilenageLogger milenageLogger,
        final AVPContainer eutranAuthInfo,
        final String visitedPlmnId,
        final String imsi,
        final Sim sim) throws DiameterErrorAnswerException {
        final List<AVP> vectors = new ArrayList<>();
        if (eutranAuthInfo == null) {
            return vectors;
        }

        final var numberOfVectors = Math.min(numberOfRequestedVectors(request, eutranAuthInfo), MAX_NUMBER_OF_VECTORS);
        for (var index = 0; index < numberOfVectors; index++) {
            try {
                final var isFirstVector = index == 0;
                final var vector = authenticator.generate4GAuthenticationVector(
                    milenageLogger,
                    new MilenageInput(AMF_4G, isFirstVector ? resyncInfo(eutranAuthInfo) : null, sim),
                    visitedPlmnId
                );
                vectors.add(AVP.create(KEY_E_UTRAN_VECTOR, List.of(
                    AVP.create(KEY_ITEM_NUMBER, index + 1L),
                    AVP.create(KEY_RAND, vector.rand()),
                    AVP.create(KEY_XRES, vector.xres()),
                    AVP.create(KEY_AUTN, vector.autn()),
                    AVP.create(KEY_KASME, vector.kasme())
                )));
            } catch (final Exception ex) {
                LOGGER.error("{}: unable to generate 4g authentication vector", imsi, ex);
                throw new DiameterErrorAnswerException(DiameterMessageFactory.createExperimentalResultAnswer(request, VENDOR_ID_3GPP, EXP_RES_DIAMETER_AUTHENTICATION_DATA_UNAVAILABLE));
            }
        }

        return vectors;
    }

    private List<AVP> buildUtranVectors(
        final AuthenticationInformationRequest.In request, final MilenageLogger milenageLogger,
        final AVPContainer utranGeranAuthInfo,
        final String imsi,
        final Sim sim) throws DiameterErrorAnswerException {
        final List<AVP> vectors = new ArrayList<>();
        if (utranGeranAuthInfo == null) {
            return vectors;
        }

        final var numberOfVectors = Math.min(numberOfRequestedVectors(request, utranGeranAuthInfo), MAX_NUMBER_OF_VECTORS);
        for (var index = 0; index < numberOfVectors; index++) {
            try {
                final var vector = authenticator.generate3GAuthenticationVector(
                    milenageLogger,
                    new MilenageInput(
                        AMF_3G,
                        // only resync for the first vector:
                        index == 0 ? resyncInfo(utranGeranAuthInfo) : null,
                        sim
                    )
                );
                vectors.add(AVP.create(KEY_UTRAN_VECTOR, List.of(
                    AVP.create(KEY_ITEM_NUMBER, index + 1L),
                    AVP.create(KEY_RAND, vector.rand()),
                    AVP.create(KEY_XRES, vector.xres()),
                    AVP.create(KEY_AUTN, vector.autn()),
                    AVP.create(KEY_CONFIDENTIALITY_KEY, vector.confidentialityKey()),
                    AVP.create(KEY_INTEGRITY_KEY, vector.integrityKey())
                )));
            } catch (final Exception ex) {
                LOGGER.error("{}: unable to generate 3g authentication vector", imsi, ex);
                throw new DiameterErrorAnswerException(DiameterMessageFactory.createExperimentalResultAnswer(request, VENDOR_ID_3GPP, EXP_RES_DIAMETER_AUTHENTICATION_DATA_UNAVAILABLE));
            }
        }

        return vectors;
    }

    private static int numberOfRequestedVectors(final AuthenticationInformationRequest.In request, final AVPContainer requestedAuthInfo) throws DiameterErrorAnswerException {
        final var numberOfRequestedVectors = requestedAuthInfo.findAVP(KEY_NUMBER_OF_REQUESTED_VECTORS);
        if (numberOfRequestedVectors == null) {
            throw AnswerFactory.missingAvp(request, AVP.create(KEY_NUMBER_OF_REQUESTED_VECTORS, 0L));
        }

        if (numberOfRequestedVectors.getDataAsUnsignedInt() == 0) {
            final var answer = DiameterMessageFactory.createErrorAnswer(request, RES_DIAMETER_INVALID_AVP_VALUE);
            answer.setFailedAVP(List.of(numberOfRequestedVectors));
            throw new DiameterErrorAnswerException(answer);
        }

        return (int) numberOfRequestedVectors.getDataAsUnsignedInt();
    }

    private static ResyncInfo resyncInfo(final AVPContainer requestedAuthInfo) {
        final var reSynchronizationInfo = requestedAuthInfo.findAVP(KEY_RE_SYNCHRONIZATION_INFO);
        if (reSynchronizationInfo == null) {
            return null;
        }

        return ResyncInfo.fromConcatenated(reSynchronizationInfo.getData());
    }
}
