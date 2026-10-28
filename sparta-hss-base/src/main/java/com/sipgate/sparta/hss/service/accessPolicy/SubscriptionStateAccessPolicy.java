package com.sipgate.sparta.hss.service.accessPolicy;


import com.sipgate.sparta.hss.persistence.SimDao;
import com.sipgate.sparta.hss.persistence.entities.Sim;

import java.util.Optional;

public class SubscriptionStateAccessPolicy implements AccessPolicy {

    private final SimDao simDao;

    public SubscriptionStateAccessPolicy(final SimDao simDao) {
        this.simDao = simDao;
    }

    @Override
    public Optional<Boolean> isEutranAccessAllowed(final String imsi, final String mcc) {
        return isSimActive(imsi);
    }

    @Override
    public Optional<Boolean> isImsAccessAllowed(final String imsi) {
        return isSimActive(imsi);
    }

    private Optional<Boolean> isSimActive(final String imsi) {
        return simDao.getSimByImsiString(imsi)
            .map(Sim::isActive);
    }
}
