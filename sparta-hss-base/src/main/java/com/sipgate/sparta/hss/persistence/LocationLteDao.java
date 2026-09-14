package com.sipgate.sparta.hss.persistence;

import com.sipgate.sparta.hss.persistence.entities.LocationLTE;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocationLteDao {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocationLteDao.class);

    @PersistenceContext(unitName = "default")
    private final EntityManager entityManager;

    private final ImsiDao imsiDao;


    public LocationLteDao(final EntityManager entityManager, final ImsiDao imsiDao) {
        this.entityManager = entityManager;
        this.imsiDao = imsiDao;
    }

    public Optional<LocationLTE> storeMmeLocation(final String imsi, final String mmeHost, final String mmeRealm, final String visitedPlmnId, final String tac) {
        final var maybeOldLocation = getExistingLocation(imsi);
        if (maybeOldLocation.isPresent()) {
            final var oldLocation = maybeOldLocation.get().copy();
            final var newLocation = maybeOldLocation.get()
                    .setMmeRealm(mmeRealm)
                    .setMmeHostname(mmeHost)
                    .setVisitedPlmnId(visitedPlmnId)
                    .setLastUpdate(new Date())
                    .setTac(tac);
            entityManager.persist(newLocation);
            return Optional.of(oldLocation);
        }

        // save new location as none exists yet
        final var maybeImsi = imsiDao.getImsiEntity(imsi);
        if (maybeImsi.isEmpty()) {
            LOGGER.warn("tried to set MME location for non-existent imsi {}", imsi);
            return Optional.empty();
        }

        entityManager.persist(new LocationLTE(maybeImsi.get(), mmeHost, mmeRealm, visitedPlmnId, tac).setLastUpdate(new Date()));
        return Optional.empty();
    }


    public void removeExistingMmeLocation(final LocationLTE location) {
        entityManager.remove(location);
    }


    public Optional<LocationLTE> getLocation(final String imsi) {
        return getExistingLocation(imsi);
    }


    private Optional<LocationLTE> getExistingLocation(final String imsi) {
        try {
            final var query = entityManager.createQuery("from LocationLTE l where l.imsi.imsi = :imsi", LocationLTE.class)
                    .setParameter("imsi", imsi);
            return Optional.of(query.getSingleResult());
        } catch (final NoResultException e) {
            return Optional.empty();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<LocationLTE> getLocationByMsisdn(final String msisdn) {
        try {
            final var query = entityManager.createQuery("from LocationLTE l where l.imsi.sim.msisdn.msisdn = :msisdn");
            query.setParameter("msisdn", msisdn);
            return Optional.of(((LocationLTE) query.getSingleResult()));
        } catch (final NoResultException e) {
            return Optional.empty();
        } catch (final Exception e) {
            LOGGER.error("could not query LocationLTE for MSISDN {}", msisdn, e);
            return Optional.empty();
        }
    }

    public record MccMncEsimCountResult(String mcc, String mnc, Long cntEsim, Long cntPlastic) {}

    /// Counts the subscribers seen per visited network in the last day, split into eSIM and
    /// plastic SIM. Which IMSIs are eSIMs is an operator numbering-plan decision, so the ranges
    /// come from the caller; without ranges every subscriber counts as plastic.
    public List<MccMncEsimCountResult> getMccMncEsimCounts(final List<ImsiRange> esimImsiRanges) {
        final var esimPredicateBuilder = new StringBuilder();
        for (var i = 0; i < esimImsiRanges.size(); i++) {
            if (i > 0) {
                esimPredicateBuilder.append(" or ");
            }
            esimPredicateBuilder.append("i.imsi between :esimFrom").append(i).append(" and :esimTo").append(i);
        }
        final var esimPredicate = esimImsiRanges.isEmpty() ? "1 = 0" : esimPredicateBuilder.toString();

        final var query = entityManager.createQuery("""
           select
                substring(l.visitedPlmnId, 1, 3) as mcc,
                substring(l.visitedPlmnId, 4, 3) as mnc,
                count(l.lastUpdate) filter (where %s) as cntEsim,
                count(l.lastUpdate) filter (where not(%s)) as cntPlastic
            from
                LocationLTE l
            inner join
                Imsi i on l.imsi = i
            where
                l.lastUpdate > current_timestamp() - 1 DAY
            group by l.visitedPlmnId
           """.formatted(esimPredicate, esimPredicate), MccMncEsimCountResult.class);
        for (var i = 0; i < esimImsiRanges.size(); i++) {
            query.setParameter("esimFrom" + i, esimImsiRanges.get(i).from());
            query.setParameter("esimTo" + i, esimImsiRanges.get(i).to());
        }
        return query.getResultList();
    }
}
