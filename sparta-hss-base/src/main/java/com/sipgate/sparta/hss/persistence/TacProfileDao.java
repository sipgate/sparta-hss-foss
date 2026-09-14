package com.sipgate.sparta.hss.persistence;

import com.sipgate.sparta.hss.persistence.entities.TacProfile;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class TacProfileDao {

    @PersistenceContext(unitName = "default")
    private EntityManager entityManager;


    public TacProfileDao(final EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Optional<String> findByTac(final String tac) {
        final var query = entityManager
                .createQuery("from TacProfile tpf where tpf.tac = :tac", TacProfile.class)
                .setParameter("tac", tac);
        try {
            return Optional.of(query.getSingleResult().getName());
        } catch (final NoResultException ex) {
            return Optional.empty();
        }
    }

    public Map<String, String> findAll() {
        final var query = entityManager
                .createQuery("from TacProfile tpf", TacProfile.class);
        return query.getResultList()
                .stream()
                .collect(Collectors.toMap(TacProfile::getTac, TacProfile::getName));
    }

    public void saveOrUpdate(final String tac, final String profileName) {
        final var tacProfileNames = entityManager.createQuery("from TacProfile tpf where tpf.tac = :tac", TacProfile.class)
                .setParameter("tac", tac)
                .getResultList();

        if (tacProfileNames.isEmpty()) {
            entityManager.persist(new TacProfile(tac, profileName, new Date()));
            return;
        }

        final var tacProfileName = tacProfileNames.getFirst();
        tacProfileName.setName(profileName);
        tacProfileName.setLastUpdate(new Date());
        entityManager.persist(tacProfileName);
    }

    public void delete(final String tac) {
        entityManager.createQuery("select tpf from TacProfile tpf where tpf.tac = :tac", TacProfile.class)
                .setParameter("tac", tac)
                .getResultList()
                .forEach(entityManager::remove);
    }
}
