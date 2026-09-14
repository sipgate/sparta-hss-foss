package com.sipgate.sparta.hss.diameter.s6a.pur;

import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.VENDOR_ID_3GPP;

import com.sipgate.sparta.diameter._3gpp.common._3gppConstants;
import com.sipgate.sparta.diameter._3gpp.s6a.messages.PurgeUeAnswer;
import com.sipgate.sparta.diameter._3gpp.s6a.messages.PurgeUeRequest;
import com.sipgate.sparta.diameter.base.core.DiameterConstants;
import com.sipgate.sparta.diameter.base.core.DiameterMessageFactory;
import com.sipgate.sparta.diameter.base.session.DiameterErrorAnswerException;
import com.sipgate.sparta.hss.diameter.client.RegisterableDiameterHandler;
import com.sipgate.sparta.hss.persistence.LocationLteDao;
import com.sipgate.sparta.hss.persistence.SimDao;
import jakarta.transaction.Transactional;
import java.util.concurrent.CompletableFuture;

public class PurgeUeHandler implements RegisterableDiameterHandler<PurgeUeRequest.In, PurgeUeAnswer.Out> {

    private final SimDao simDao;

    private final LocationLteDao locationLteDao;

    public PurgeUeHandler(final SimDao simDao, final LocationLteDao locationLteDao) {
        this.simDao = simDao;
        this.locationLteDao = locationLteDao;
    }

    @Override
    public Class<PurgeUeRequest.In> requestType() {
        return PurgeUeRequest.In.class;
    }

    @Override
    @Transactional
    public CompletableFuture<PurgeUeAnswer.Out> handle(final PurgeUeRequest.In request) {
        try {
            return CompletableFuture.completedFuture(doHandle(request));
        } catch (final Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private PurgeUeAnswer.Out doHandle(final PurgeUeRequest.In request) throws DiameterErrorAnswerException {
        final var imsi = request.getUserName();

        if (simDao.getSimByImsiString(imsi).isEmpty()) {
            throw new DiameterErrorAnswerException(
                DiameterMessageFactory.createExperimentalResultAnswer(request, VENDOR_ID_3GPP, _3gppConstants.EXP_RES_DIAMETER_ERROR_USER_UNKNOWN));
        }

        // Only the MME that currently serves the subscriber may purge the location (3GPP TS 29.272):
        // a PUR from any other origin host/realm must not remove the stored registration.
        locationLteDao.getLocation(imsi)
                .filter(loc -> loc.getMmeHostname().equals(request.getOriginHost()))
                .filter(loc -> loc.getMmeRealm().equals(request.getOriginRealm()))
                .ifPresent(locationLteDao::removeExistingMmeLocation);

        return DiameterMessageFactory.createAnswer(request, DiameterConstants.RES_DIAMETER_SUCCESS);
    }
}
