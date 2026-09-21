package com.sipgate.e2e.requests;

import com.sipgate.sparta.diameter._3gpp.common._3gppConstants;
import com.sipgate.sparta.diameter._3gpp.s6a.S6aConstants;
import com.sipgate.sparta.diameter._3gpp.s6a.messages.AuthenticationInformationRequest;
import com.sipgate.sparta.diameter._3gpp.s6a.messages.NotifyRequest;
import com.sipgate.sparta.diameter._3gpp.s6a.messages.PurgeUeRequest;
import com.sipgate.sparta.diameter._3gpp.s6a.messages.UpdateLocationRequest;
import com.sipgate.sparta.diameter.base.core.DiameterConstants;
import com.sipgate.sparta.diameter.base.core.Request;
import com.sipgate.sparta.diameter.base.core.avp.AVP;
import com.sipgate.sparta.diameter.base.core.avp.AVPKey;
import com.sipgate.sparta.diameter.base.core.avp.mixins.HasAuthSessionStateAVP;
import com.sipgate.sparta.diameter.base.core.avp.mixins.HasVendorSpecificApplicationIdAVP;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class S6aRequestFactory {

    public static final String MME_HOST = "mmec01.mmegi8001.mme.epc.mnc001.mcc001.3gppnetwork.org";
    public static final String MME_REALM = "epc.mnc001.mcc001.3gppnetwork.org";

    static final String HSS_HOST = "hss.e2e.epc.mnc001.mcc001.3gppnetwork.org";
    static final String HSS_REALM = "epc.mnc001.mcc001.3gppnetwork.org";

    private static final AtomicLong SESSION_COUNTER = new AtomicLong(1);

    private S6aRequestFactory() {}

    public static UpdateLocationRequest.Out updateLocation(final String imsi, final byte[] visitedPlmnId) {
        final var ulr = new UpdateLocationRequest.Out();
        stampHeader(ulr);
        ulr.setUserName(imsi);
        ulr.setVisitedPlmnId(visitedPlmnId);
        // ULR-Flags bit 1 (0x2) = S6a-Indicator: the request comes from an MME.
        ulr.setUlrFlags(0x2L);
        return ulr;
    }

    public static AuthenticationInformationRequest.Out authenticationInformation(
        final String imsi, final byte[] visitedPlmnId, final long numberOfRequestedVectors) {
        final var air = new AuthenticationInformationRequest.Out();
        stampHeader(air);
        air.setUserName(imsi);
        air.setVisitedPlmnId(visitedPlmnId);
        air.setRequestedEutranAuthenticationInfo(List.of(
            AVP.create(new AVPKey(S6aConstants.AVP_NUMBER_OF_REQUESTED_VECTORS, _3gppConstants.VENDOR_ID_3GPP),
                numberOfRequestedVectors)));
        return air;
    }

    public static PurgeUeRequest.Out purgeUe(final String imsi) {
        final var pur = new PurgeUeRequest.Out();
        stampHeader(pur);
        pur.setUserName(imsi);
        return pur;
    }

    public static NotifyRequest.Out notifyRequest(final String imsi) {
        final var nor = new NotifyRequest.Out();
        stampHeader(nor);
        nor.setUserName(imsi);
        return nor;
    }

    private static void stampHeader(final Request<?> request) {
        request.setSessionId(MME_HOST + ";" + 1 + ";" + SESSION_COUNTER.getAndIncrement());
        request.setOriginHost(MME_HOST);
        request.setOriginRealm(MME_REALM);
        request.setDestinationHost(HSS_HOST);
        request.setDestinationRealm(HSS_REALM);
        if (request instanceof final HasVendorSpecificApplicationIdAVP vsai) {
            vsai.setVendorSpecificApplicationId(List.of(
                AVP.create(new AVPKey(DiameterConstants.AVP_VENDOR_ID, 0), (long) _3gppConstants.VENDOR_ID_3GPP),
                AVP.create(new AVPKey(DiameterConstants.AVP_AUTH_APPLICATION_ID, 0), (long) S6aConstants.APP_ID_S6A_S6D)));
        }
        if (request instanceof final HasAuthSessionStateAVP authState) {
            authState.setAuthSessionState(DiameterConstants.AUTH_SESSION_STATE_NOT_MAINTAINED);
        }
    }
}
