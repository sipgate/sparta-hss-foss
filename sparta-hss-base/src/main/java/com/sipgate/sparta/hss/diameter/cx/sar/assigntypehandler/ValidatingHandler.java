package com.sipgate.sparta.hss.diameter.cx.sar.assigntypehandler;

import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.VENDOR_ID_3GPP;

import com.sipgate.sparta.diameter._3gpp.common._3gppConstants;
import com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants;
import com.sipgate.sparta.diameter._3gpp.cxdx.messages.ServerAssignmentAnswer;
import com.sipgate.sparta.diameter._3gpp.cxdx.messages.ServerAssignmentRequest;
import com.sipgate.sparta.diameter.base.core.DiameterMessageFactory;
import com.sipgate.sparta.diameter.base.core.avp.AVP;
import com.sipgate.sparta.diameter.base.core.avp.AVPKey;
import com.sipgate.sparta.diameter.base.session.DiameterErrorAnswerException;
import com.sipgate.sparta.hss.diameter.common.AnswerFactory;
import com.sipgate.sparta.hss.diameter.cx.sar.identity.PublicIdentity;
import com.sipgate.sparta.hss.diameter.cx.sar.service.SimService;

public class ValidatingHandler extends ServerAssignTypeHandler {

    private static final AVPKey KEY_PUBLIC_IDENTITY = new AVPKey(CxDxConstants.AVP_PUBLIC_IDENTITY, VENDOR_ID_3GPP);
    private static final AVPKey KEY_SERVER_NAME = new AVPKey(CxDxConstants.AVP_SERVER_NAME, VENDOR_ID_3GPP);

    private final ServerAssignTypeHandler delegate;
    private final SimService simService;

    public ValidatingHandler(final ServerAssignTypeHandler delegate, final SimService simService) {
        this.delegate = delegate;
        this.simService = simService;
    }

    @Override
    public ServerAssignmentAnswer.Out handle(final ServerAssignmentRequest.In request, final PublicIdentity identity)
        throws DiameterErrorAnswerException
    {
        assert identity != null;

        final var scscf = request.getServerName();
        if (scscf == null) {
            throw AnswerFactory.missingAvp(request, AVP.create(KEY_SERVER_NAME, ""));
        }

        final var publicIdentities = request.getPublicIdentities();
        if (publicIdentities.size() > 1) {
            // RFC 6733 §7.5: Failed-AVP must contain a copy of the first instance of the
            // offending AVP that exceeded the maximum number of occurrences.
            final var avp = AVP.create(KEY_PUBLIC_IDENTITY, publicIdentities.get(1));
            throw AnswerFactory.avpOccursTooManyTimes(request, avp);
        }

        final var imsi = getImsiFromRequest(request);
        if (!simService.isImsiKnown(imsi) || !identity.isKnown()) {
            throw new DiameterErrorAnswerException(
                DiameterMessageFactory.createExperimentalResultAnswer(request, VENDOR_ID_3GPP, _3gppConstants.EXP_RES_DIAMETER_ERROR_USER_UNKNOWN));
        }

        if (!identity.isAssociatedWithImsi(imsi)) {
            throw new DiameterErrorAnswerException(
                DiameterMessageFactory.createExperimentalResultAnswer(request, VENDOR_ID_3GPP, _3gppConstants.EXP_RES_DIAMETER_ERROR_IDENTITIES_DONT_MATCH));
        }

        return delegate.handle(request, identity);
    }



}
