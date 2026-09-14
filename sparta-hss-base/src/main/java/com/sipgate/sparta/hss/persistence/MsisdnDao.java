package com.sipgate.sparta.hss.persistence;

import com.sipgate.sparta.hss.persistence.entities.Imsi;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MsisdnDao {
    private static final Logger LOGGER = LoggerFactory.getLogger(MsisdnDao.class);

    @PersistenceContext(unitName = "default")
    private EntityManager entityManager;


    public MsisdnDao(final EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public boolean isMsisdnKnown(final String msisdn) {
        try {
            final var query = entityManager.createQuery("from Msisdn m where m.msisdn = :msisdn");
            query.setParameter("msisdn", msisdn);
            query.getSingleResult();
            return true;
        } catch (final NoResultException e) {
            return false;
        } catch (final Exception e) {
            LOGGER.error("could not check msisdn {}", msisdn, e);
            return false;
        }
    }

    public Optional<String> getMsisdnByImsi(final String imsi) {
        try {
            final var query = entityManager.createQuery("from Imsi where imsi = :imsi");
            query.setParameter("imsi", imsi);
            return Optional.of(((Imsi) query.getSingleResult()).getSim().getMsisdn().getMsisdn());
        } catch (final NoResultException | NullPointerException e) {
            return Optional.empty();
        } catch (final Exception e) {
            LOGGER.error("could not query msisdn for imsi {}", imsi, e);
            return Optional.empty();
        }
    }
}
