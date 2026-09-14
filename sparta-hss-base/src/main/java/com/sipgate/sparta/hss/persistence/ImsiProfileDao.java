package com.sipgate.sparta.hss.persistence;

import com.sipgate.sparta.hss.persistence.entities.ImsiProfile;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class ImsiProfileDao {

    @PersistenceContext(unitName = "default")
    private EntityManager entityManager;

    private final ImsiDao imsiDao;


    public ImsiProfileDao(final EntityManager entityManager, final ImsiDao imsiDao) {
        this.entityManager = entityManager;
        this.imsiDao = imsiDao;
    }

    public Map<String /*imsi-string*/, String /* profileName */> findAll() {
        final var query = entityManager
                .createQuery("from ImsiProfile ipf", ImsiProfile.class);
        final var result = query.getResultList();
        return result
                .stream()
                .collect(Collectors.toMap(e -> e.getImsi().getImsi(), ImsiProfile::getName));
    }

    public void saveOrUpdate(final String imsi, final String profileName) {
        final var imsiEntity = imsiDao.getImsiEntity(imsi).orElseThrow(() -> new IllegalArgumentException("unknown imsi " + imsi));
        final var imsiProfileNames = entityManager.createQuery("from ImsiProfile ipf where ipf.imsi = :imsi", ImsiProfile.class)
                .setParameter("imsi", imsiEntity)
                .getResultList();

        if (imsiProfileNames.isEmpty()) {
            entityManager.persist(new ImsiProfile(imsiEntity, profileName, new Date()));
            return;
        }

        final var imsiProfileName = imsiProfileNames.getFirst();
        imsiProfileName.setName(profileName);
        imsiProfileName.setLastUpdate(new Date());
        entityManager.persist(imsiProfileName);
    }

    /**
     * Accepts non-existing imsis.
     */
    public void delete(final String imsi) {
        entityManager.createQuery("select ipf from ImsiProfile ipf join ipf.imsi i where i.imsi = :imsi", ImsiProfile.class)
                .setParameter("imsi", imsi)
                .getResultList()
                .forEach(ipf -> {
                    // Since we use a newer hibernate the behavior of @XyzToOne seems to have changed.
                    // We remove the parent from the context to prevent cascading "un-scheduling entity deletion".
                    entityManager.detach(ipf.getImsi());
                    entityManager.remove(ipf);
                });
    }

    public Optional<String> findByImsi(final String imsi) {
        final var query = entityManager
                .createQuery("from ImsiProfile ipf where ipf.imsi.imsi = :imsi", ImsiProfile.class)
                .setParameter("imsi", imsi);
        try {
            return Optional.of(query.getSingleResult().getName());
        } catch (final NoResultException ex) {
            return Optional.empty();
        }
    }
}
