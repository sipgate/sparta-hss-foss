package com.sipgate.sparta.hss.persistence;

import com.sipgate.sparta.hss.persistence.entities.RoamingBlockedEntity;
import com.sipgate.sparta.hss.persistence.entities.RoamingBlockedEvent;
import com.sipgate.sparta.hss.persistence.entities.RoamingBlockedImsiOverride;
import com.sipgate.sparta.hss.persistence.entities.RoamingBlockedImsiOverrideLte;
import com.sipgate.sparta.hss.persistence.entities.RoamingBlockedLocation;
import com.sipgate.sparta.hss.persistence.entities.RoamingBlockedLocationLte;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jakarta.transaction.Transactional;

public class BlockRoamingDao {

    private static final Duration BLOCKED_EVENT_TTL = Duration.ofMinutes(5);

    @PersistenceContext(unitName = "default")
    private EntityManager entityManager;


    public BlockRoamingDao(final EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Collection<RoamingBlockedImsiOverride> findImsiOverrides(final String imsi) {
        return entityManager
                .createQuery("from RoamingBlockedImsiOverride where imsi=:imsi", RoamingBlockedImsiOverride.class)
                .setParameter("imsi", imsi)
                .getResultList();
    }

    public Optional<RoamingBlockedImsiOverrideLte> findImsiOverrideLte(final String imsi, final String mcc, final String mnc) {
        return entityManager
                .createQuery("from RoamingBlockedImsiOverrideLte where imsi=:imsi and mcc=:mcc and mnc=:mnc", RoamingBlockedImsiOverrideLte.class)
                .setParameter("imsi", imsi)
                .setParameter("mcc", mcc)
                .setParameter("mnc", mnc)
                .getResultStream()
                .findFirst();
    }

    public boolean isNetworkBlockedLte(final String mcc, final String mnc) {
        return entityManager
                .createQuery("from RoamingBlockedLocationLte where mcc=:mcc and mnc=:mnc", RoamingBlockedLocationLte.class)
                .setParameter("mcc", mcc)
                .setParameter("mnc", mnc)
                // we could use getResultList, but it is not as expressive as reading "isPresent"
                .getResultStream()
                .findAny()
                .isPresent();
    }

    public BlockRoamingDao deleteAll() {
        entityManager.createQuery("delete from RoamingBlockedImsiOverride").executeUpdate();
        entityManager.createQuery("delete from RoamingBlockedImsiOverrideLte").executeUpdate();
        entityManager.createQuery("delete from RoamingBlockedLocation").executeUpdate();
        entityManager.createQuery("delete from RoamingBlockedLocationLte").executeUpdate();
        return this;
    }

    public <T extends RoamingBlockedEntity> BlockRoamingDao insert(final Collection<T> data) {
        data.forEach(entityManager::persist);
        return this;
    }

    public Map<String, List<? extends RoamingBlockedEntity>> dumpAll() {
        return Map.of(
                "imsiOverrides", entityManager.createQuery("from RoamingBlockedImsiOverride", RoamingBlockedImsiOverride.class).getResultList(),
                "imsiOverridesLte", entityManager.createQuery("from RoamingBlockedImsiOverrideLte", RoamingBlockedImsiOverrideLte.class).getResultList(),
                "blockedLocations", entityManager.createQuery("from RoamingBlockedLocation", RoamingBlockedLocation.class).getResultList(),
                "blockedLocationsLte", entityManager.createQuery("from RoamingBlockedLocationLte", RoamingBlockedLocationLte.class).getResultList()
        );
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void insertRoamingBlockedEvent(final String imsi, final String mcc, final String component) {
        entityManager.persist(new RoamingBlockedEvent(imsi, mcc, component, Instant.now()));
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public int deleteRoamingBlockedEvents(final String imsi, final String component) {
        return entityManager
                .createQuery("DELETE FROM RoamingBlockedEvent r WHERE r.imsi = :imsi AND r.component = :component")
                .setParameter("imsi", imsi)
                .setParameter("component", component)
                .executeUpdate();
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public List<RoamingBlockedEvent> deleteExpiredRoamingBlockedEvents(final String component) {
        final Instant threshold = Instant.now().minus(BLOCKED_EVENT_TTL);
        final List<RoamingBlockedEvent> expired = entityManager
                .createQuery("FROM RoamingBlockedEvent r WHERE r.createdAt < :threshold AND r.component = :component", RoamingBlockedEvent.class)
                .setParameter("threshold", threshold)
                .setParameter("component", component)
                .getResultList();
        expired.forEach(entityManager::remove);
        return expired;
    }
}
