package com.sipgate.sparta.hss.service.volte.location;

import com.sipgate.sparta.hss.persistence.LocationLteDao;
import com.sipgate.sparta.hss.persistence.MsisdnScscfDao;
import com.sipgate.sparta.hss.persistence.entities.LocationLTE;
import java.util.Optional;

public class LocationService {
    private final MsisdnScscfDao scscfDao;
    private final LocationLteDao locationLteDao;

    public LocationService(final MsisdnScscfDao scscfDao, final LocationLteDao locationLteDao) {
        this.scscfDao = scscfDao;
        this.locationLteDao = locationLteDao;
    }

    public Optional<String> getSipLocation(final String msisdn) {
        return scscfDao.getScscfRecent(msisdn);
    }

    public Optional<String> getVisitedPlmnId(final String msisdn) {
        final var location = locationLteDao.getLocationByMsisdn(msisdn);
        return location.map(LocationLTE::getVisitedPlmnId);
    }
}
