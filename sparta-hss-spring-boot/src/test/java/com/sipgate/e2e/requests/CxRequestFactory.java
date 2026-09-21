package com.sipgate.e2e.requests;

import com.sipgate.sparta.diameter._3gpp.common._3gppConstants;
import com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants;
import com.sipgate.sparta.diameter._3gpp.cxdx.messages.MultimediaAuthRequest;
import com.sipgate.sparta.diameter._3gpp.cxdx.messages.ServerAssignmentRequest;
import com.sipgate.sparta.diameter.base.core.DiameterConstants;
import com.sipgate.sparta.diameter.base.core.Request;
import com.sipgate.sparta.diameter.base.core.avp.AVP;
import com.sipgate.sparta.diameter.base.core.avp.AVPKey;
import com.sipgate.sparta.diameter.base.core.avp.mixins.HasAuthSessionStateAVP;
import com.sipgate.sparta.diameter.base.core.avp.mixins.HasVendorSpecificApplicationIdAVP;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class CxRequestFactory {

    public static final String SCSCF_HOST = "scscf01.ims.mnc001.mcc001.3gppnetwork.org";
    public static final String SCSCF_REALM = "ims.mnc001.mcc001.3gppnetwork.org";
    public static final String SCSCF_SERVER_NAME = "sip:" + SCSCF_HOST + ":5060";

    public static final String SIP_AUTH_SCHEME_AKA_V1_MD5 = "Digest-AKAv1-MD5";

    private static final AtomicLong SESSION_COUNTER = new AtomicLong(1);

    private CxRequestFactory() {}

    public static MultimediaAuthRequest.Out multimediaAuth(final String privateIdentity) {
        final var mar = new MultimediaAuthRequest.Out();
        stampHeader(mar);
        mar.setUserName(privateIdentity);
        mar.setServerName(SCSCF_SERVER_NAME);
        mar.setSipAuthDataItem(List.of(
            AVP.create(new AVPKey(CxDxConstants.AVP_SIP_AUTHENTICATION_SCHEME, _3gppConstants.VENDOR_ID_3GPP),
                SIP_AUTH_SCHEME_AKA_V1_MD5)));
        return mar;
    }

    public static ServerAssignmentRequest.Out serverAssignment(
        final String privateIdentity, final String publicIdentity, final int assignmentType) {
        final var sar = new ServerAssignmentRequest.Out();
        stampHeader(sar);
        sar.setUserName(privateIdentity);
        sar.addPublicIdentity(publicIdentity);
        sar.setServerName(SCSCF_SERVER_NAME);
        sar.setServerAssignmentType(assignmentType);
        return sar;
    }

    public static ServerAssignmentRequest.Out serverAssignment(
        final String privateIdentity, final String publicIdentity, final int assignmentType,
        final String ipSmGwName, final String ipSmGwRealm) {
        final var sar = serverAssignment(privateIdentity, publicIdentity, assignmentType);
        sar.addAVP(AVP.create(
            new AVPKey(_3gppConstants.AVP_SERVING_NODE, _3gppConstants.VENDOR_ID_3GPP),
            List.of(
                AVP.create(new AVPKey(_3gppConstants.AVP_IP_SM_GW_NAME, _3gppConstants.VENDOR_ID_3GPP), ipSmGwName),
                AVP.create(new AVPKey(_3gppConstants.AVP_IP_SM_GW_REALM, _3gppConstants.VENDOR_ID_3GPP), ipSmGwRealm))));
        return sar;
    }

    private static void stampHeader(final Request<?> request) {
        request.setSessionId(SCSCF_HOST + ";" + 1 + ";" + SESSION_COUNTER.getAndIncrement());
        request.setOriginHost(SCSCF_HOST);
        request.setOriginRealm(SCSCF_REALM);
        request.setDestinationHost(S6aRequestFactory.HSS_HOST);
        request.setDestinationRealm(S6aRequestFactory.HSS_REALM);
        if (request instanceof final HasVendorSpecificApplicationIdAVP vsai) {
            vsai.setVendorSpecificApplicationId(List.of(
                AVP.create(new AVPKey(DiameterConstants.AVP_VENDOR_ID, 0), (long) _3gppConstants.VENDOR_ID_3GPP),
                AVP.create(new AVPKey(DiameterConstants.AVP_AUTH_APPLICATION_ID, 0), (long) CxDxConstants.APP_ID_CX_DX)));
        }
        if (request instanceof final HasAuthSessionStateAVP authState) {
            authState.setAuthSessionState(DiameterConstants.AUTH_SESSION_STATE_NOT_MAINTAINED);
        }
    }
}
