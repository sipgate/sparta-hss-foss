package com.sipgate.sparta.hss.diameter.common;

import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.AVP_IP_SM_GW_NAME;
import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.AVP_IP_SM_GW_REALM;
import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.AVP_SERVING_NODE;
import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.VENDOR_ID_3GPP;

import com.sipgate.sparta.diameter.base.core.IncomingRequest;
import com.sipgate.sparta.diameter.base.core.avp.AVP;
import com.sipgate.sparta.diameter.base.core.avp.AVPKey;
import com.sipgate.sparta.diameter.base.core.avp.GroupedAVP;
import com.sipgate.sparta.diameter.base.session.DiameterErrorAnswerException;

/// IP-SM-GW the UCN reports for MT SMS over IP (AWBD-601).
public record ServingNodeInfo(String ipSmGwName, String ipSmGwRealm) {

    private static final AVPKey KEY_SERVING_NODE   = new AVPKey(AVP_SERVING_NODE, VENDOR_ID_3GPP);
    private static final AVPKey KEY_IP_SM_GW_NAME  = new AVPKey(AVP_IP_SM_GW_NAME, VENDOR_ID_3GPP);
    private static final AVPKey KEY_IP_SM_GW_REALM = new AVPKey(AVP_IP_SM_GW_REALM, VENDOR_ID_3GPP);

    public static ServingNodeInfo from(final IncomingRequest<?> request) throws DiameterErrorAnswerException {
        final var servingNode = request.findAVP(KEY_SERVING_NODE);
        if (!(servingNode instanceof final GroupedAVP grouped)) {
            return null;
        }
        final var name  = requireChild(request, grouped.findAVP(KEY_IP_SM_GW_NAME),  KEY_IP_SM_GW_NAME);
        final var realm = requireChild(request, grouped.findAVP(KEY_IP_SM_GW_REALM), KEY_IP_SM_GW_REALM);
        return new ServingNodeInfo(name, realm);
    }

    private static String requireChild(
        final IncomingRequest<?> request,
        final AVP child,
        final AVPKey key) throws DiameterErrorAnswerException
    {
        if (child == null) {
            throw AnswerFactory.missingAvp(request, AVP.create(key, ""));
        }
        final var value = child.getDataAsString();
        if (value == null || value.isBlank()) {
            throw AnswerFactory.invalidAvpValue(request, AVP.create(key, ""));
        }
        return value;
    }

    public boolean hasIpSmGw() {
        return ipSmGwName != null && ipSmGwRealm != null && !ipSmGwName.isBlank() && !ipSmGwRealm.isBlank();
    }
}
