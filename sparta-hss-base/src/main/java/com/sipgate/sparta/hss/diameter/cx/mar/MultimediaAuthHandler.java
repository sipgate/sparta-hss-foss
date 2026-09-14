package com.sipgate.sparta.hss.diameter.cx.mar;

import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.VENDOR_ID_3GPP;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.*;
import static jakarta.xml.bind.DatatypeConverter.parseHexBinary;
import static jakarta.xml.bind.DatatypeConverter.printHexBinary;

import com.sipgate.sparta.diameter._3gpp.common._3gppConstants;
import com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants;
import com.sipgate.sparta.diameter._3gpp.cxdx.messages.MultimediaAuthRequest;
import com.sipgate.sparta.diameter._3gpp.cxdx.messages.MultimediaAuthAnswer;
import com.sipgate.sparta.diameter._3gpp.cxdx.messages.RegistrationTerminationRequest;
import com.sipgate.sparta.diameter.base.core.DiameterConstants;
import com.sipgate.sparta.diameter.base.core.DiameterMessageFactory;
import com.sipgate.sparta.diameter.base.core.avp.AVP;
import com.sipgate.sparta.diameter.base.core.avp.AVPKey;
import com.sipgate.sparta.diameter.base.session.DiameterErrorAnswerException;
import com.sipgate.sparta.hss.diameter.client.DiameterSessions;
import com.sipgate.sparta.hss.diameter.client.RegisterableDiameterHandler;
import com.sipgate.sparta.hss.diameter.common.AnswerFactory;
import com.sipgate.sparta.hss.diameter.common.auth.AkaVector;
import com.sipgate.sparta.hss.diameter.common.auth.Authenticator;
import com.sipgate.sparta.hss.diameter.common.auth.MilenageLogger;
import com.sipgate.sparta.hss.diameter.common.auth.MilenageInput;
import com.sipgate.sparta.hss.diameter.common.auth.ResyncInfo;
import com.sipgate.sparta.hss.diameter.cx.rtr.RegistrationTerminationEvent;
import com.sipgate.sparta.hss.event.EventPublisher;
import com.sipgate.sparta.hss.persistence.ImsiScscfDao;
import com.sipgate.sparta.hss.persistence.SimDao;
import com.sipgate.sparta.hss.persistence.entities.ImsiScscf;
import jakarta.transaction.Transactional;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultimediaAuthHandler implements RegisterableDiameterHandler<MultimediaAuthRequest.In, MultimediaAuthAnswer.Out> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultimediaAuthHandler.class);
    private static final int IMSI_LEN = 15;

    // 3GPP TS 33.203 version 17.1.0 Release 17 V17.1.0 (2022-05)
    // 6.1 Authentication and key agreement
    // The HN shall choose the IMS AKA scheme for authenticating an IM subscriber accessing through UMTS.
    // The AMF field can be used in the same way as in TS 33.102 [1].
    // => set AMF to 0000 just as in AMF_3G (see AuthenticationInfoHandler.java)
    private static final byte[] AMF = parseHexBinary("0000");

    private static final AVPKey KEY_SIP_AUTHENTICATION_SCHEME = new AVPKey(AVP_SIP_AUTHENTICATION_SCHEME, VENDOR_ID_3GPP);
    private static final AVPKey KEY_SIP_AUTHORIZATION = new AVPKey(AVP_SIP_AUTHORIZATION, VENDOR_ID_3GPP);
    private static final AVPKey KEY_CONFIDENTIALITY_KEY = new AVPKey(AVP_CONFIDENTIALITY_KEY, VENDOR_ID_3GPP);
    private static final AVPKey KEY_INTEGRITY_KEY = new AVPKey(AVP_INTEGRITY_KEY, VENDOR_ID_3GPP);
    private static final AVPKey KEY_SIP_AUTHENTICATE = new AVPKey(AVP_SIP_AUTHENTICATE, VENDOR_ID_3GPP);
    private static final AVPKey KEY_SIP_ITEM_NUMBER = new AVPKey(AVP_SIP_ITEM_NUMBER, VENDOR_ID_3GPP);
    private static final AVPKey KEY_SIP_AUTH_DATA_ITEM = new AVPKey(AVP_SIP_AUTH_DATA_ITEM, VENDOR_ID_3GPP);
    private static final AVPKey KEY_USER_NAME = new AVPKey(DiameterConstants.AVP_USER_NAME, 0);

    private final SimDao simDao;

    private final ImsiScscfDao imsiScscfDao;

    private final Authenticator authenticator;
    private final EventPublisher eventPublisher;
    private final DiameterSessions diameterSessions;


    public MultimediaAuthHandler(
        final SimDao simDao,
        final ImsiScscfDao imsiScscfDao,
        final Authenticator authenticator,
        final EventPublisher eventPublisher, final DiameterSessions diameterSessions)
    {
        this.simDao = simDao;

        this.imsiScscfDao = imsiScscfDao;
        this.authenticator = authenticator;
        this.eventPublisher = eventPublisher;
        this.diameterSessions = diameterSessions;
    }


    @Override
    public Class<MultimediaAuthRequest.In> requestType() {
        return MultimediaAuthRequest.In.class;
    }

    @Override
    @Transactional
    public CompletableFuture<MultimediaAuthAnswer.Out> handle(final MultimediaAuthRequest.In request) {
        final var userName = request.getUserName();
        if (userName == null) {
            return CompletableFuture.failedFuture(AnswerFactory.missingAvp(request, AVP.create(KEY_USER_NAME, "")));
        }
        if (userName.length() < IMSI_LEN) {
            return CompletableFuture.failedFuture(AnswerFactory.invalidAvpValue(request, request.findAVP(KEY_USER_NAME)));
        }
        final var imsi = userName.substring(0, IMSI_LEN);
        try (final var resyncLogger = new MilenageLogger(imsi)) {
            return doHandle(resyncLogger, request);
        } catch (final Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private CompletableFuture<MultimediaAuthAnswer.Out> doHandle(final MilenageLogger milenageLogger, final MultimediaAuthRequest.In request)
        throws Exception
    {
        // TODO: Wird das noch gebraucht?
        /* Helpful RFC for this implementation: RFC 4740. section 8.8 */
        if (request.getSipAuthDataItem() == null) {
            LOGGER.warn("MAR without SipAuthDataItem received - cannot process.");
            throw AnswerFactory.missingAvp(request, AVP.create(KEY_SIP_AUTH_DATA_ITEM, List.of()));
        }

        final var sipAuthScheme = request.getSipAuthDataItem().findAVP(KEY_SIP_AUTHENTICATION_SCHEME);
        if (sipAuthScheme == null) {
            throw AnswerFactory.missingAvp(request, AVP.create(KEY_SIP_AUTHENTICATION_SCHEME, ""));
        }
        if (!"Digest-AKAv1-MD5".equals(sipAuthScheme.getDataAsString())) {
            throw new DiameterErrorAnswerException(
                DiameterMessageFactory.createExperimentalResultAnswer(request, VENDOR_ID_3GPP, CxDxConstants.EXP_RES_DIAMETER_ERROR_AUTH_SCHEME_NOT_SUPPORTED));
        }

        final var sipAuthorizationAvp = request.getSipAuthDataItem().findAVP(KEY_SIP_AUTHORIZATION);
        final var resyncInfo = createResyncInfo(sipAuthorizationAvp == null ? null : sipAuthorizationAvp.getData());
        milenageLogger.log(resyncInfo == null ? "[MA91] no resync info present" : "[MA92] resync requested");

        final var imsi = request.getUserName().substring(0, IMSI_LEN);
        // FOR-UPDATE lock: the vector generation below read-increments-writes the SIM's SQN, so
        // concurrent MARs for the same IMSI must serialise here or their SQNs overlap (TS 33.102).
        final var maybeSim = simDao.getSimByImsiStringForUpdate(imsi);

        if (maybeSim.isEmpty()) {
            throw new DiameterErrorAnswerException(DiameterMessageFactory.createExperimentalResultAnswer(
                request, VENDOR_ID_3GPP, _3gppConstants.EXP_RES_DIAMETER_ERROR_USER_UNKNOWN));
        }

        try {
            final var sim = maybeSim.get();
            final var publicIdentity = "tel:+" + sim.getMsisdn().getMsisdn();

            cancelPreviousRegistrationIfChanged(request, imsi, publicIdentity);

            final var v = authenticator.generate3GAuthenticationVector(milenageLogger, new MilenageInput(AMF, resyncInfo, sim));
            final var akaVector = new AkaVector(v.rand(), v.autn(), v.xres(), v.confidentialityKey(), v.integrityKey(), Arrays.copyOfRange(v.autn(), 0, 6));

            final var rand = akaVector.rand();
            final var autn = akaVector.autn();

            final var sipAuthenticate = new byte[rand.length + autn.length];
            System.arraycopy(rand, 0, sipAuthenticate, 0, rand.length);
            System.arraycopy(autn, 0, sipAuthenticate, rand.length, autn.length);

            final var answer = DiameterMessageFactory.createAnswer(request, DiameterConstants.RES_DIAMETER_SUCCESS);
            answer.addSipAuthDataItem(List.of(
                AVP.create(KEY_CONFIDENTIALITY_KEY, akaVector.ck()),
                AVP.create(KEY_INTEGRITY_KEY, akaVector.ik()),
                AVP.create(KEY_SIP_AUTHORIZATION, akaVector.xres()),
                AVP.create(KEY_SIP_AUTHENTICATE, sipAuthenticate),
                AVP.create(KEY_SIP_AUTHENTICATION_SCHEME, "Digest-AKAv1-MD5"),
                AVP.create(KEY_SIP_ITEM_NUMBER, 0L)
            ));
            answer.setSipNumberAuthItems(1);
            answer.setUserName(request.getUserName());
            answer.setPublicIdentity(publicIdentity);

            return CompletableFuture.completedFuture(answer);
        } catch (Exception _) {
            throw new DiameterErrorAnswerException(DiameterMessageFactory.createExperimentalResultAnswer(request, VENDOR_ID_3GPP, CxDxConstants.EXP_RES_DIAMETER_ERROR_SERVING_NODE_FEATURE_UNSUPPORTED));
        }

    }

    /// fire-and-forget, the answer is only logged
    private void cancelPreviousRegistrationIfChanged(final MultimediaAuthRequest.In request, final String imsi, final String publicIdentity)
    {
        final var maybePreviousScscf = imsiScscfDao.getScscf(imsi);
        if (maybePreviousScscf.isEmpty()) {
            return;
        }

        final var previousScscf = maybePreviousScscf.get();
        if (
            Objects.equals(previousScscf.getScscf(), request.getServerName())
                && Objects.equals(previousScscf.getDiameterHost(), request.getOriginHost())
                && Objects.equals(previousScscf.getDiameterRealm(), request.getOriginRealm())
        ) {
            return;
        }

        imsiScscfDao.clearScscf(imsi);
        sendRegistrationTermination(request.getUserName(), publicIdentity, previousScscf);
    }

    private void sendRegistrationTermination(final String userName, final String publicIdentity, final ImsiScscf previousScscf)
    {
        final var rtr = diameterSessions.createRequest(RegistrationTerminationRequest.Out.class,
            DiameterConstants.AUTH_SESSION_STATE_NOT_MAINTAINED,
            previousScscf.getDiameterHost(),
            previousScscf.getDiameterRealm(),
            _3gppConstants.VENDOR_ID_3GPP,
            CxDxConstants.APP_ID_CX_DX
        );

        rtr.setUserName(userName);
        rtr.setDeregistrationReason(List.of(
            AVP.create(new AVPKey(AVP_REASON_CODE, VENDOR_ID_3GPP), REASON_CODE_NEW_SERVER_ASSIGNED),
            AVP.create(new AVPKey(AVP_REASON_INFO, VENDOR_ID_3GPP), "UE de-registration due to new s-cscf assignment")));
        rtr.addPublicIdentity(publicIdentity);

        // Hand the request to the outbound listener so it is sent off the inbound request thread.
        eventPublisher.publish(new RegistrationTerminationEvent(
            rtr, "user=" + userName + ", scscf=" + previousScscf.getDiameterHost()));
    }

    private static ResyncInfo createResyncInfo(final byte[] sipAuthorization) {
        LOGGER.info(
            "IMS SIM sqn number resync required: {}. sipAuthenticate: {}",
            sipAuthorization != null,
            sipAuthorization == null ? null : printHexBinary(sipAuthorization)
        );
        return ResyncInfo.fromConcatenated(sipAuthorization);
    }

}
