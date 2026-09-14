package com.sipgate.sparta.hss.persistence;

import com.sipgate.sparta.hss.persistence.entities.Imsi;
import com.sipgate.sparta.hss.persistence.entities.ImsiScscf;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import java.util.Date;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImsiScscfDao {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImsiScscfDao.class);

    @PersistenceContext(unitName = "default")
    private EntityManager entityManager;

    public ImsiScscfDao(final EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Optional<ImsiScscf> getScscf(final String imsi) {
        try {
            final var query = entityManager.createQuery("from ImsiScscf s where s.imsi.imsi = :imsi");
            query.setParameter("imsi", imsi);
            return Optional.of(((ImsiScscf) query.getSingleResult()));
        } catch (final NoResultException e) {
            return Optional.empty();
        } catch (final Exception e) {
            LOGGER.error("could not query S-CSCF for IMSI {}", imsi, e);
            return Optional.empty();
        }
    }

    public void setScscf(final String imsi, final String scscf, final String diameterHost, final String diameterRealm) {
        final var imsiScscf = getOrCreateImsiScscfEntityByImsi(imsi);
        if (imsiScscf == null) {
            LOGGER.warn("tried to set S-CSCF for non-existent imsi {}", imsi);
            return;
        }

        imsiScscf.setScscf(scscf);
        imsiScscf.setDiameterHost(diameterHost);
        imsiScscf.setDiameterRealm(diameterRealm);
        imsiScscf.setLastUpdate(new Date());
        entityManager.persist(imsiScscf);
    }

    public void clearScscf(final String imsi) {

        try {
            final var query = entityManager.createQuery("from ImsiScscf s where s.imsi.imsi = :imsi");
            query.setParameter("imsi", imsi);
            entityManager.remove(query.getSingleResult());
        } catch (final NoResultException ignored) {
            // ignored
        }

    }

    private ImsiScscf getOrCreateImsiScscfEntityByImsi(final String imsi) {
        try {
            return (ImsiScscf) entityManager
                    .createQuery("from ImsiScscf where imsi.imsi = :imsi")
                    .setParameter("imsi", imsi)
                    .getSingleResult();

        } catch (final NoResultException e) {
            try {
                final var imsiEntity = (Imsi) entityManager.createQuery("from Imsi where imsi = :imsi")
                        .setParameter("imsi", imsi)
                        .getSingleResult();
                final var imsiScscf = new ImsiScscf();
                imsiScscf.setImsi(imsiEntity);
                return imsiScscf;
            } catch (final NoResultException f) {
                return null;
            }
        }
    }
}
