package com.sipgate.sparta.hss.diameter.cx.sar.service;

import com.sipgate.sparta.hss.persistence.ImsiDao;
import com.sipgate.sparta.hss.persistence.MsisdnDao;
import java.util.Optional;

public class SimService {

    private final MsisdnDao msisdnDao;

    private final ImsiDao imsiDao;

    public SimService(final MsisdnDao msisdnDao, final ImsiDao imsiDao) {
        this.msisdnDao = msisdnDao;
        this.imsiDao = imsiDao;
    }

    public boolean isImsiKnown(final String imsi) {
        return imsiDao.isImsiKnown(imsi);
    }

    public boolean isMsisdnKnown(final String msisdn) {
        return msisdnDao.isMsisdnKnown(msisdn);
    }

    public Optional<String> getMsisdnByImsi(final String imsi) {
        return msisdnDao.getMsisdnByImsi(imsi);
    }
}
