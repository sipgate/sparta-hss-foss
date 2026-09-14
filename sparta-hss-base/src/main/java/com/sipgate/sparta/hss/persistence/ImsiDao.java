package com.sipgate.sparta.hss.persistence;

import com.sipgate.sparta.hss.persistence.entities.Imsi;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImsiDao {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImsiDao.class);

    @PersistenceContext(unitName = "default")
    private EntityManager entityManager;


    public ImsiDao(final EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public boolean isImsiKnown(final String imsi) {
        try {
            final var query = entityManager.createQuery("from Imsi i where i.imsi = :imsi");
            query.setParameter("imsi", imsi);
            query.getSingleResult();
            return true;
        } catch (final NoResultException e) {
            return false;
        } catch (final Exception e) {
            LOGGER.error("could not check imsi {}", imsi, e);
            return false;
        }
    }

    public Optional<Imsi> getImsiEntity(final String imsi) {
        try {
            final var imsiEntity = entityManager.createQuery("from Imsi where imsi = :imsi", Imsi.class)
                    .setParameter("imsi", imsi)
                    .getSingleResult();
            return Optional.of(imsiEntity);
        } catch (final NoResultException e) {
            return Optional.empty();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }
}
