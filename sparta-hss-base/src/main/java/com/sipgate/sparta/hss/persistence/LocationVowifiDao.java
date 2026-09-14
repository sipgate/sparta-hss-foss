package com.sipgate.sparta.hss.persistence;

import com.sipgate.sparta.hss.persistence.entities.Imsi;
import com.sipgate.sparta.hss.persistence.entities.LocationVowifi;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import java.util.Date;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocationVowifiDao {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocationVowifiDao.class);

    @PersistenceContext(unitName = "default")
    private EntityManager entityManager;

    public LocationVowifiDao(final EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Optional<LocationVowifi> getAaaServer(final String imsi) {
        try {
            final var query = entityManager.createQuery("from LocationVowifi s where s.imsi.imsi = :imsi");
            query.setParameter("imsi", imsi);
            return Optional.of(((LocationVowifi) query.getSingleResult()));
        } catch (final NoResultException e) {
            return Optional.empty();
        }
    }

    public void store(final String imsi, final String aaaServerName, final String diameterHost, final String diameterRealm) {
        final var locationVowifi = getOrCreateLocationVowifiEntityByImsi(imsi);
        if (locationVowifi == null) {
            LOGGER.warn("tried to set AAA Server for non-existent imsi {}", imsi);
            return;
        }

        locationVowifi.setAaaServerName(aaaServerName);
        locationVowifi.setDiameterHost(diameterHost);
        locationVowifi.setDiameterRealm(diameterRealm);
        locationVowifi.setLastUpdate(new Date());
        entityManager.persist(locationVowifi);
    }

    public void clearAaaServer(final String imsi) {

        try {
            final var query = entityManager.createQuery("from LocationVowifi s where s.imsi.imsi = :imsi");
            query.setParameter("imsi", imsi);
            entityManager.remove(query.getSingleResult());
        } catch (final NoResultException ignored) {
            // ignored
        }

    }

    private LocationVowifi getOrCreateLocationVowifiEntityByImsi(final String imsi) {
        try {
            return (LocationVowifi) entityManager
                    .createQuery("from LocationVowifi where imsi.imsi = :imsi")
                    .setParameter("imsi", imsi)
                    .getSingleResult();

        } catch (final NoResultException e) {
            try {
                final var imsiEntity = (Imsi) entityManager.createQuery("from Imsi where imsi = :imsi")
                        .setParameter("imsi", imsi)
                        .getSingleResult();
                final var locationVowifi = new LocationVowifi();
                locationVowifi.setImsi(imsiEntity);
                return locationVowifi;
            } catch (final NoResultException f) {
                return null;
            }
        }
    }
}
