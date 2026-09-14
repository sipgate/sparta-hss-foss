package com.sipgate.sparta.hss.persistence;

import com.sipgate.sparta.hss.persistence.entities.RoamingBlockedEvent;
import com.sipgate.sparta.hss.persistence.entities.RoamingBlockedImsiOverride;
import com.sipgate.sparta.hss.persistence.entities.RoamingBlockedImsiOverrideLte;
import com.sipgate.sparta.hss.persistence.entities.RoamingBlockedLocation;
import com.sipgate.sparta.hss.persistence.entities.RoamingBlockedLocationLte;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Persistence;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class BlockRoamingDaoTest {

    private EntityTransaction transaction;
    private EntityManager entityManager;
    private BlockRoamingDao underTest;

    @BeforeEach
    void setUp() {
        final var factory = Persistence.createEntityManagerFactory("integration-test");
        entityManager = factory.createEntityManager();
        transaction = entityManager.getTransaction();

        transaction.begin();
        underTest = new BlockRoamingDao(entityManager);
    }

    @AfterEach
    void tearDown() {
        transaction.rollback();
        entityManager.close();
    }

    @Test
    void itShouldInsertAndFindImsiOverrides() {
        final var imsi = "1234567890";
        final var roamingBlockedImsiOverride = new RoamingBlockedImsiOverride(null, imsi, "prefix", "roaming", "reason");

        underTest.insert(List.of(roamingBlockedImsiOverride));
        entityManager.flush();

        final var found = underTest.findImsiOverrides(imsi);

        assertThat(found).hasSize(1);
        assertThat(found.iterator().next().getImsi()).isEqualTo(imsi);
    }

    @Test
    void itShouldInsertAndFindImsiOverrideLte() {
        final var imsi = "1234567890";
        final var mcc = "262";
        final var mnc = "01";
        final var roamingBlockedImsiOverrideLte = new RoamingBlockedImsiOverrideLte(null, imsi, mcc, mnc, "roaming", "reason");

        underTest.insert(List.of(roamingBlockedImsiOverrideLte));
        entityManager.flush();

        final var found = underTest.findImsiOverrideLte(imsi, mcc, mnc);

        assertThat(found).isPresent();
        assertThat(found.get().getImsi()).isEqualTo(imsi);
        assertThat(found.get().getMcc()).isEqualTo(mcc);
        assertThat(found.get().getMnc()).isEqualTo(mnc);
    }

    @Test
    void itShouldBlockNetworkLte() {
        final var mcc = "262";
        final var mnc = "01";
        final var roamingBlockedLocationLte = new RoamingBlockedLocationLte(null, mcc, mnc, "reason");

        underTest.insert(List.of(roamingBlockedLocationLte));
        entityManager.flush();

        assertThat(underTest.isNetworkBlockedLte(mcc, mnc)).isTrue();
    }

    @Test
    void itShouldNotBlockUnknownNetworkLte() {
        final var mcc = "262";
        final var mnc = "01";

        assertThat(underTest.isNetworkBlockedLte(mcc, mnc)).isFalse();
    }

    @Test
    void itDumpsAll() {
        // GIVEN
        final var imsiOverride = new RoamingBlockedImsiOverride(null, "1", "p1", "r1", "r1");
        final var imsiOverrideLte = new RoamingBlockedImsiOverrideLte(null, "2", "262", "01", "r2", "r2");
        final var blockedLocation = new RoamingBlockedLocation(null, "p2", "r3");
        final var blockedLocationLte = new RoamingBlockedLocationLte(null, "262", "02", "r4");

        underTest.insert(List.of(imsiOverride, imsiOverrideLte, blockedLocation, blockedLocationLte));
        entityManager.flush();

        // WHEN
        final var dump = underTest.dumpAll();

        // THEN
        assertThat(dump)
                .containsOnlyKeys("imsiOverrides", "imsiOverridesLte", "blockedLocations", "blockedLocationsLte")
                .contains(Map.entry("imsiOverrides", List.of(imsiOverride)))
                .contains(Map.entry("imsiOverridesLte", List.of(imsiOverrideLte)))
                .contains(Map.entry("blockedLocations", List.of(blockedLocation)))
                .contains(Map.entry("blockedLocationsLte", List.of(blockedLocationLte)))
        ;
    }

    private static final String EVENT_IMSI = "262034860104857";
    private static final String EVENT_OTHER_IMSI = "262034860100001";
    private static final String EVENT_MCC = "262";
    private static final String EVENT_OTHER_MCC = "234";
    private static final String COMPONENT = "hss";
    private static final String OTHER_COMPONENT = "hlr";

    @Test
    void insertRoamingBlockedEventPersistsRowWithCurrentTimestamp() {
        final Instant before = Instant.now();

        underTest.insertRoamingBlockedEvent(EVENT_IMSI, EVENT_MCC, COMPONENT);
        entityManager.flush();

        final List<RoamingBlockedEvent> actual = findAllEvents();
        assertThat(actual).hasSize(1);
        assertThat(actual.get(0).getImsi()).isEqualTo(EVENT_IMSI);
        assertThat(actual.get(0).getMcc()).isEqualTo(EVENT_MCC);
        assertThat(actual.get(0).getComponent()).isEqualTo(COMPONENT);
        assertThat(actual.get(0).getCreatedAt()).isBetween(before, Instant.now());
    }

    @Test
    void insertRoamingBlockedEventAcceptsRepeatAttemptsForSameImsiAndMcc() {
        underTest.insertRoamingBlockedEvent(EVENT_IMSI, EVENT_MCC, COMPONENT);
        underTest.insertRoamingBlockedEvent(EVENT_IMSI, EVENT_MCC, COMPONENT);
        underTest.insertRoamingBlockedEvent(EVENT_IMSI, EVENT_MCC, COMPONENT);
        entityManager.flush();

        assertThat(findAllEvents()).hasSize(3);
    }

    @Test
    void deleteRoamingBlockedEventsRemovesAllRowsForImsiAndReturnsCount() {
        underTest.insertRoamingBlockedEvent(EVENT_IMSI, EVENT_MCC, COMPONENT);
        underTest.insertRoamingBlockedEvent(EVENT_IMSI, EVENT_OTHER_MCC, COMPONENT);
        underTest.insertRoamingBlockedEvent(EVENT_OTHER_IMSI, EVENT_MCC, COMPONENT);
        entityManager.flush();

        final int deleted = underTest.deleteRoamingBlockedEvents(EVENT_IMSI, COMPONENT);

        assertThat(deleted).isEqualTo(2);
        assertThat(findAllEvents())
            .extracting(RoamingBlockedEvent::getImsi)
            .containsExactly(EVENT_OTHER_IMSI);
    }

    @Test
    void deleteRoamingBlockedEventsReturnsZeroWhenNoRowsMatch() {
        underTest.insertRoamingBlockedEvent(EVENT_OTHER_IMSI, EVENT_MCC, COMPONENT);
        entityManager.flush();

        final int deleted = underTest.deleteRoamingBlockedEvents(EVENT_IMSI, COMPONENT);

        assertThat(deleted).isZero();
        assertThat(findAllEvents()).hasSize(1);
    }

    @Test
    void deleteRoamingBlockedEventsOnlyAffectsEventsFromGivenComponent() {
        underTest.insertRoamingBlockedEvent(EVENT_IMSI, EVENT_MCC, COMPONENT);
        underTest.insertRoamingBlockedEvent(EVENT_IMSI, EVENT_MCC, OTHER_COMPONENT);
        entityManager.flush();

        final int deleted = underTest.deleteRoamingBlockedEvents(EVENT_IMSI, COMPONENT);

        assertThat(deleted).isEqualTo(1);
        assertThat(findAllEvents())
            .extracting(RoamingBlockedEvent::getComponent)
            .containsExactly(OTHER_COMPONENT);
    }

    @Test
    void deleteExpiredRoamingBlockedEventsRemovesRowsOlderThanFiveMinutesAndReturnsThem() {
        final Instant now = Instant.now();
        persistEventAt(EVENT_IMSI, EVENT_MCC, COMPONENT, now.minus(6, ChronoUnit.MINUTES));
        persistEventAt(EVENT_OTHER_IMSI, EVENT_OTHER_MCC, COMPONENT, now.minus(10, ChronoUnit.MINUTES));
        persistEventAt(EVENT_IMSI, EVENT_OTHER_MCC, COMPONENT, now.minus(1, ChronoUnit.MINUTES));
        entityManager.flush();

        final List<RoamingBlockedEvent> expired = underTest.deleteExpiredRoamingBlockedEvents(COMPONENT);

        assertThat(expired)
            .extracting(RoamingBlockedEvent::getImsi, RoamingBlockedEvent::getMcc)
            .containsExactlyInAnyOrder(
                tuple(EVENT_IMSI, EVENT_MCC),
                tuple(EVENT_OTHER_IMSI, EVENT_OTHER_MCC));
        assertThat(findAllEvents())
            .extracting(RoamingBlockedEvent::getImsi, RoamingBlockedEvent::getMcc)
            .containsExactly(tuple(EVENT_IMSI, EVENT_OTHER_MCC));
    }

    @Test
    void deleteExpiredRoamingBlockedEventsReturnsEmptyListWhenNothingExpired() {
        persistEventAt(EVENT_IMSI, EVENT_MCC, COMPONENT, Instant.now().minus(1, ChronoUnit.MINUTES));
        entityManager.flush();

        assertThat(underTest.deleteExpiredRoamingBlockedEvents(COMPONENT)).isEmpty();
        assertThat(findAllEvents()).hasSize(1);
    }

    @Test
    void deleteExpiredRoamingBlockedEventsOnlyAffectsEventsFromGivenComponent() {
        final Instant now = Instant.now();
        persistEventAt(EVENT_IMSI, EVENT_MCC, COMPONENT, now.minus(6, ChronoUnit.MINUTES));
        persistEventAt(EVENT_OTHER_IMSI, EVENT_OTHER_MCC, OTHER_COMPONENT, now.minus(6, ChronoUnit.MINUTES));
        entityManager.flush();

        final List<RoamingBlockedEvent> expired = underTest.deleteExpiredRoamingBlockedEvents(COMPONENT);

        assertThat(expired).hasSize(1);
        assertThat(expired.get(0).getComponent()).isEqualTo(COMPONENT);
        assertThat(findAllEvents())
            .extracting(RoamingBlockedEvent::getComponent)
            .containsExactly(OTHER_COMPONENT);
    }

    private List<RoamingBlockedEvent> findAllEvents() {
        return entityManager
            .createQuery("FROM RoamingBlockedEvent ORDER BY id", RoamingBlockedEvent.class)
            .getResultList();
    }

    private void persistEventAt(final String imsi, final String mcc, final String component, final Instant createdAt) {
        entityManager.persist(new RoamingBlockedEvent(imsi, mcc, component, createdAt));
    }
}
