package com.sipgate.sparta.hss.persistence;

import com.sipgate.sparta.hss.persistence.entities.Sim;
import jakarta.persistence.*;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimDao {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimDao.class);

    @PersistenceContext(unitName = "default")
    private final EntityManager entityManager;

    public SimDao(final EntityManager entityManager) {
        this.entityManager = entityManager;
    }


    public Optional<Sim> getSimByImsiString(final String imsi) {
        final var imsiQuery = entityManager.createQuery("select i.sim from Imsi i where i.imsi = :imsi");
        imsiQuery.setParameter("imsi", imsi);
        imsiQuery.setMaxResults(1);

        try {
            return Optional.of((Sim) imsiQuery.getSingleResult());
        } catch (final NoResultException e) {
            return Optional.empty();
        }
        catch (final PersistenceException exception) {
            LOGGER.warn("unable to query sim from db: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    /// Like {@link #getSimByImsiString} but takes a PESSIMISTIC_WRITE (SELECT ... FOR UPDATE) lock on
    /// the SIM row. Callers that read-increment-write the SQN (MAR authentication-vector generation)
    /// must use this inside their transaction so concurrent requests for the same IMSI serialise —
    /// otherwise two vectors get computed from the same SQN and the sequence numbers overlap. Held
    /// until commit; a DB-level lock so it also serialises across HSS instances.
    public Optional<Sim> getSimByImsiStringForUpdate(final String imsi) {
        final var imsiQuery = entityManager.createQuery("select i.sim from Imsi i where i.imsi = :imsi");
        imsiQuery.setParameter("imsi", imsi);
        imsiQuery.setMaxResults(1);
        imsiQuery.setLockMode(LockModeType.PESSIMISTIC_WRITE);

        try {
            return Optional.of((Sim) imsiQuery.getSingleResult());
        } catch (final NoResultException e) {
            return Optional.empty();
        }
        catch (final PersistenceException exception) {
            LOGGER.warn("unable to query sim from db: {}", exception.getMessage());
            return Optional.empty();
        }
    }
}
