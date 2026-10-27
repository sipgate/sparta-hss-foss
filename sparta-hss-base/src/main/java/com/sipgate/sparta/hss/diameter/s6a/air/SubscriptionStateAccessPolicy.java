package com.sipgate.sparta.hss.diameter.s6a.air;


import com.sipgate.sparta.hss.persistence.SimDao;
import com.sipgate.sparta.hss.persistence.entities.Sim;

import java.util.Optional;

public class SubscriptionStateAccessPolicy implements EutranAccessPolicy {

    private final SimDao simDao;

    public SubscriptionStateAccessPolicy(final SimDao simDao) {
        this.simDao = simDao;
    }

    @Override
    public Optional<Boolean> isEutranAccessAllowed(final String imsi, final String mcc) {
        return simDao.getSimByImsiString(imsi)
            .map(Sim::isActive);
    }
}
