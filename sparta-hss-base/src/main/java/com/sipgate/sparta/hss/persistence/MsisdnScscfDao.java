package com.sipgate.sparta.hss.persistence;

import com.sipgate.sparta.hss.persistence.entities.ImsiScscf;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import java.sql.Time;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MsisdnScscfDao {

    private static final Logger LOGGER = LoggerFactory.getLogger(MsisdnScscfDao.class);

    @PersistenceContext(unitName = "default")
    private EntityManager entityManager;


    public MsisdnScscfDao(final EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Optional<String> getScscfRecent(final String msisdn) {
        try {
            final var query = entityManager.createQuery("from ImsiScscf s where s.imsi.sim.msisdn.msisdn = :msisdn and s.lastUpdate > :time");
            query.setParameter("msisdn", msisdn);
            query.setParameter("time", Time.from(Instant.now().minus(1, ChronoUnit.DAYS)));
            return Optional.of(((ImsiScscf) query.getSingleResult()).getScscf());
        } catch (final NoResultException e) {
            return Optional.empty();
        } catch (final Exception e) {
            LOGGER.error("could not query S-CSCF for MSISDN {}", msisdn, e);
            return Optional.empty();
        }
    }

    public Optional<String> getScscf(final String msisdn) {
        try {
            final var query = entityManager.createQuery("from ImsiScscf s where s.imsi.sim.msisdn.msisdn = :msisdn");
            query.setParameter("msisdn", msisdn);
            return Optional.of(((ImsiScscf) query.getSingleResult()).getScscf());
        } catch (final NoResultException e) {
            return Optional.empty();
        } catch (final Exception e) {
            LOGGER.error("could not query S-CSCF for MSISDN {}", msisdn, e);
            return Optional.empty();
        }
    }
}
