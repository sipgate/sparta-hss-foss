package com.sipgate.sparta.hss.diameter.common;

import com.sipgate.sparta.diameter.base.core.DiameterConstants;
import com.sipgate.sparta.diameter.base.core.DiameterMessageFactory;
import com.sipgate.sparta.diameter.base.core.IncomingRequest;
import com.sipgate.sparta.diameter.base.core.avp.AVP;
import com.sipgate.sparta.diameter.base.session.DiameterErrorAnswerException;
import java.util.List;

import static com.sipgate.sparta.diameter.base.core.DiameterConstants.RES_DIAMETER_MISSING_AVP;

public class AnswerFactory {

    public static DiameterErrorAnswerException missingAvp(
        final IncomingRequest<?> request,
        final AVP missingAvp)
    {
        final var answer = DiameterMessageFactory.createErrorAnswer(request, RES_DIAMETER_MISSING_AVP);
        answer.setFailedAVP(List.of(missingAvp));
        return new DiameterErrorAnswerException(answer);
    }

    public static DiameterErrorAnswerException avpOccursTooManyTimes(
        final IncomingRequest<?> request,
        final AVP avp)
    {
        final var answer = DiameterMessageFactory.createErrorAnswer(request, DiameterConstants.RES_DIAMETER_AVP_OCCURS_TOO_MANY_TIMES);
        answer.setFailedAVP(List.of(avp));
        return new DiameterErrorAnswerException(answer);
    }

    public static DiameterErrorAnswerException invalidAvpValue(
        final IncomingRequest<?> request,
        final AVP avp)
    {
        final var answer = DiameterMessageFactory.createErrorAnswer(request, DiameterConstants.RES_DIAMETER_INVALID_AVP_VALUE);
        answer.setFailedAVP(List.of(avp));
        return new DiameterErrorAnswerException(answer);
    }

}
