package com.sipgate.sparta.hss.diameter.s6a.nir;

import com.sipgate.sparta.diameter._3gpp.common._3gppConstants;
import com.sipgate.sparta.diameter._3gpp.s6a.messages.NotifyAnswer;
import com.sipgate.sparta.diameter._3gpp.s6a.messages.NotifyRequest;
import com.sipgate.sparta.diameter.base.core.DiameterConstants;
import com.sipgate.sparta.diameter.base.core.DiameterMessageFactory;
import com.sipgate.sparta.diameter.base.session.DiameterErrorAnswerException;
import com.sipgate.sparta.hss.diameter.client.RegisterableDiameterHandler;
import com.sipgate.sparta.hss.persistence.SimDao;
import java.util.concurrent.CompletableFuture;

import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.VENDOR_ID_3GPP;

public class NotifyHandler
    implements RegisterableDiameterHandler<NotifyRequest.In, NotifyAnswer.Out>
{

    private final SimDao simDao;

    public NotifyHandler(final SimDao simDao) {
        this.simDao = simDao;
    }

    @Override
    public Class<NotifyRequest.In> requestType() {
        return NotifyRequest.In.class;
    }

    @Override
    public CompletableFuture<NotifyAnswer.Out> handle(final NotifyRequest.In request) {
        if (simDao.getSimByImsiString(request.getUserName()).isEmpty()) {
            return CompletableFuture.failedFuture(new DiameterErrorAnswerException(
                DiameterMessageFactory.createExperimentalResultAnswer(request, VENDOR_ID_3GPP, _3gppConstants.EXP_RES_DIAMETER_ERROR_USER_UNKNOWN)));
        }
        return CompletableFuture.completedFuture(DiameterMessageFactory.createAnswer(request, DiameterConstants.RES_DIAMETER_SUCCESS));
    }
}
