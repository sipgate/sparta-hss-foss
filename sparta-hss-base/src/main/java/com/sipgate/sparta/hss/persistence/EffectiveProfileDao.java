package com.sipgate.sparta.hss.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class EffectiveProfileDao {
    public static final String HQL_EFFECTIVE_PROFILE =
        "SELECT i.imsi, l.mmeHostname, l.mmeRealm, m.msisdn, COALESCE(ipf.name, tpf.name, 'default'), l.visitedPlmnId " +
            "FROM LocationLTE l " +
            "JOIN l.imsi i " +
            "JOIN i.sim s " +
            "JOIN s.msisdn m " +
            "LEFT JOIN i.imsiProfile ipf " +
            "LEFT JOIN l.tacProfile tpf " +
            "WHERE " +
            "    i.imsi IN (:imsis) " +
            " OR tpf.tac IN (:tacs)";
    @PersistenceContext(unitName = "default")
    private EntityManager entityManager;

    public EffectiveProfileDao(final EntityManager entityManager) {
        this.entityManager = entityManager;
    }


    public List<UpdateProfileResult> getUpdateProfileResults(Set<String> imsis, Set<String> tacs) {
        Objects.requireNonNull(imsis);
        Objects.requireNonNull(tacs);

        // HQL does not support empty IN clauses >:(. It is OK if no tacs or imsis are given though.
        if (imsis.isEmpty()) {
            imsis = Collections.singleton("");
        }
        if (tacs.isEmpty()) {
            tacs = Collections.singleton("");
        }


        final var query = entityManager.createQuery(HQL_EFFECTIVE_PROFILE, Object[].class);
        query.setParameter("imsis", imsis);
        query.setParameter("tacs", tacs);
        query.setLockMode(LockModeType.NONE);
        final var results = query.getResultList();

        return results
            .stream()
            .map(UpdateProfileResult::new)
            .collect(Collectors.toList());
    }

    public static class UpdateProfileResult {

        public UpdateProfileResult(final Object... args) {
            this.imsi = args[0].toString();
            this.mmeHostname = args[1].toString();
            this.mmeRealm = args[2].toString();
            this.msisdn = args[3].toString();
            this.profile = args[4].toString();
            this.visitedPlmnId = args[5].toString();
        }

        private final String imsi;
        private final String mmeHostname;
        private final String mmeRealm;
        private final String visitedPlmnId;
        private final String msisdn;
        private final String profile;

        public String getImsi() {
            return imsi;
        }

        public String getMmeHostname() {
            return mmeHostname;
        }

        public String getMmeRealm() {
            return mmeRealm;
        }

        public String getVisitedPlmnId() {
            return visitedPlmnId;
        }

        public String getMsisdn() {
            return msisdn;
        }

        public String getProfile() {
            return profile;
        }
    }
}
