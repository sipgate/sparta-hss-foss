package com.sipgate.sparta.hss.persistence;

import com.sipgate.sparta.hss.persistence.entities.Imsi;
import com.sipgate.sparta.hss.persistence.entities.LocationIpSmGw;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import java.util.Date;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocationIpSmGwDao {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocationIpSmGwDao.class);

    @PersistenceContext(unitName = "default")
    private EntityManager entityManager;

    public LocationIpSmGwDao(final EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Optional<LocationIpSmGw> getIpSmGw(final String imsi) {
        try {
            final var query = entityManager.createQuery("from LocationIpSmGw s where s.imsi.imsi = :imsi");
            query.setParameter("imsi", imsi);
            return Optional.of(((LocationIpSmGw) query.getSingleResult()));
        } catch (final NoResultException _) {
            return Optional.empty();
        }
    }

    public void store(final String imsi, final String ipSmGwName, final String ipSmGwRealm) {
        final var locationIpSmGw = getOrCreateLocationIpSmGwEntityByImsi(imsi);
        if (locationIpSmGw == null) {
            LOGGER.warn("tried to set IP-SM-GW for non-existent imsi {}", imsi);
            return;
        }

        locationIpSmGw.setIpSmGwName(ipSmGwName);
        locationIpSmGw.setIpSmGwRealm(ipSmGwRealm);
        locationIpSmGw.setLastUpdate(new Date());
        entityManager.persist(locationIpSmGw);
    }

    public void clearIpSmGw(final String imsi) {
        try {
            final var query = entityManager.createQuery("from LocationIpSmGw s where s.imsi.imsi = :imsi");
            query.setParameter("imsi", imsi);
            entityManager.remove(query.getSingleResult());
        } catch (final NoResultException e) {
            LOGGER.error("unable to clear IP-SM-GW for imsi {}", imsi, e);
        }
    }

    private LocationIpSmGw getOrCreateLocationIpSmGwEntityByImsi(final String imsi) {
        try {
            return (LocationIpSmGw) entityManager
                    .createQuery("from LocationIpSmGw where imsi.imsi = :imsi")
                    .setParameter("imsi", imsi)
                    .getSingleResult();

        } catch (final NoResultException _) {
            try {
                final var imsiEntity = (Imsi) entityManager.createQuery("from Imsi where imsi = :imsi")
                        .setParameter("imsi", imsi)
                        .getSingleResult();
                final var locationIpSmGw = new LocationIpSmGw();
                locationIpSmGw.setImsi(imsiEntity);
                return locationIpSmGw;
            } catch (final NoResultException e) {
                throw new IllegalStateException("Trying to store IpSmGw information for unknown IMSI.", e);
            }
        }
    }
}
