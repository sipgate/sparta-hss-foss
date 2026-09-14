package com.sipgate.sparta.hss.service.roaming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sipgate.sparta.hss.persistence.BlockRoamingDao;
import com.sipgate.sparta.hss.persistence.entities.RoamingBlockedEvent;
import com.sipgate.sparta.hss.persistence.entities.RoamingBlockedImsiOverride;
import com.sipgate.sparta.hss.persistence.entities.RoamingBlockedImsiOverrideLte;
import com.sipgate.sparta.hss.persistence.entities.RoamingBlockedLocation;
import com.sipgate.sparta.hss.persistence.entities.RoamingBlockedLocationLte;
import com.sipgate.sparta.hss.service.roaming.ImsiRule.Roaming;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Persistence;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlockRoamingServiceTest {

    private static final String IMSI = "123456789012345";
    private static final String GT_PREFIX = "4917012345";
    private static final String MCC = "260";
    private static final String MNC = "01";
    private static final String MCC_MNC = MCC + "-" + MNC;
    private static final String REASON = "blocked abroad";

    @Mock
    private BlockRoamingDao blockRoamingDao;

    private MeterRegistry meterRegistry;
    private BlockRoamingService underTest;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        underTest = new BlockRoamingService(blockRoamingDao, meterRegistry);
    }

    @Nested
    class ReplaceRules {

        @BeforeEach
        void setUp() {
            lenient()
                    .when(blockRoamingDao.deleteAll())
                    .thenReturn(blockRoamingDao);
            lenient()
                    .when(blockRoamingDao.insert(anyCollection()))
                    .thenReturn(blockRoamingDao);
        }

        @Test
        void testReplaceRules() {
            final var entityManagerFactory = Persistence.createEntityManagerFactory("integration-test");
            final var entityManager = entityManagerFactory.createEntityManager();
            final var transaction = entityManager.getTransaction();

            try {
                transaction.begin();

                final var integrationBlockRoamingDao = newBlockRoamingDao(entityManager);
                final var integrationUnderTest = new BlockRoamingService(integrationBlockRoamingDao, new SimpleMeterRegistry());

                // GIVEN
                integrationBlockRoamingDao.insert(List.of(
                        new RoamingBlockedImsiOverride(null, "111111111111111", "499999", "blocked", "stale imsi gt"),
                        new RoamingBlockedImsiOverrideLte(null, "222222222222222", "999", "99", "blocked", "stale imsi lte"),
                        new RoamingBlockedLocation(null, "488888", "stale network gt"),
                        new RoamingBlockedLocationLte(null, "888", "88", "stale network lte")
                ));
                entityManager.flush();
                entityManager.clear();

                final var network = new NetworkBlockingRule(List.of(), List.of(GT_PREFIX), REASON);
                final var imsiRule = new ImsiRule(List.of(MCC_MNC), List.of(), Roaming.ALLOWED, REASON);
                final var rules = new RoamingRules(List.of(network), Map.of(IMSI, imsiRule));

                // WHEN
                integrationUnderTest.replaceRules(rules);
                entityManager.flush();
                entityManager.clear();

                // THEN
                assertThat(entityManager.createQuery("from RoamingBlockedImsiOverride", RoamingBlockedImsiOverride.class).getResultList()).isEmpty();
                assertThat(entityManager.createQuery("from RoamingBlockedImsiOverrideLte", RoamingBlockedImsiOverrideLte.class).getResultList())
                        .singleElement()
                        .satisfies(override -> {
                            assertThat(override.getImsi()).isEqualTo(IMSI);
                            assertThat(override.getMcc()).isEqualTo(MCC);
                            assertThat(override.getMnc()).isEqualTo(MNC);
                            assertThat(override.getRoaming()).isEqualTo("allowed");
                            assertThat(override.getReason()).isEqualTo(REASON);
                        });
                assertThat(entityManager.createQuery("from RoamingBlockedLocation", RoamingBlockedLocation.class).getResultList())
                        .singleElement()
                        .satisfies(location -> {
                            assertThat(location.getGtPrefix()).isEqualTo(GT_PREFIX);
                            assertThat(location.getReason()).isEqualTo(REASON);
                        });
                assertThat(entityManager.createQuery("from RoamingBlockedLocationLte", RoamingBlockedLocationLte.class).getResultList()).isEmpty();
            } finally {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                entityManager.close();
                entityManagerFactory.close();
            }
        }

        @Test
        void shouldAlwaysDeleteAllBeforeInsertingNewRules() {
            // GIVEN
            final var rules = new RoamingRules(List.of(), Map.of());

            // WHEN
            underTest.replaceRules(rules);

            // THEN
            verify(blockRoamingDao).deleteAll();
        }

        @Test
        void shouldInsertBlockedLocationForNetworkGtPrefix() {
            // GIVEN
            final var network = new NetworkBlockingRule(List.of(), List.of(GT_PREFIX), REASON);
            final var rules = new RoamingRules(List.of(network), Map.of());

            // WHEN
            underTest.replaceRules(rules);

            // THEN
            verify(blockRoamingDao).insert(List.of(new RoamingBlockedLocation(null, GT_PREFIX, REASON)));
        }

        @Test
        void shouldInsertBlockedLocationLteForNetworkMccMnc() {
            // GIVEN
            final var network = new NetworkBlockingRule(List.of(MCC_MNC), List.of(), REASON);
            final var rules = new RoamingRules(List.of(network), Map.of());

            // WHEN
            underTest.replaceRules(rules);

            // THEN
            verify(blockRoamingDao).insert(List.of(new RoamingBlockedLocationLte(null, MCC, MNC, REASON)));
        }

        @Test
        void shouldInsertBothLocationTypesWhenNetworkHasBothGtPrefixAndMccMnc() {
            // GIVEN
            final var network = new NetworkBlockingRule(List.of(MCC_MNC), List.of(GT_PREFIX), REASON);
            final var rules = new RoamingRules(List.of(network), Map.of());

            // WHEN
            underTest.replaceRules(rules);

            // THEN
            verify(blockRoamingDao).insert(List.of(new RoamingBlockedLocation(null, GT_PREFIX, REASON)));
            verify(blockRoamingDao).insert(List.of(new RoamingBlockedLocationLte(null, MCC, MNC, REASON)));
        }

        @Test
        void shouldInsertImsiOverrideForBlockedImsiWithGtPrefix() {
            // GIVEN
            final var imsiRule = new ImsiRule(List.of(), List.of(GT_PREFIX), Roaming.BLOCKED, REASON);
            final var rules = new RoamingRules(List.of(), Map.of(IMSI, imsiRule));

            // WHEN
            underTest.replaceRules(rules);

            // THEN
            verify(blockRoamingDao).insert(List.of(new RoamingBlockedImsiOverride(null, IMSI, GT_PREFIX, "blocked", REASON)));
        }

        @Test
        void shouldInsertImsiOverrideLteForAllowedImsiWithMccMnc() {
            // GIVEN
            final var imsiRule = new ImsiRule(List.of(MCC_MNC), List.of(), Roaming.ALLOWED, REASON);
            final var rules = new RoamingRules(List.of(), Map.of(IMSI, imsiRule));

            // WHEN
            underTest.replaceRules(rules);

            // THEN
            verify(blockRoamingDao).insert(List.of(new RoamingBlockedImsiOverrideLte(null, IMSI, MCC, MNC, "allowed", REASON)));
        }

        @Test
        void shouldInsertNetworkBlockWithImsiAllowedException() {
            // GIVEN: a network is blocked, but one specific IMSI is allowed through
            final var network = new NetworkBlockingRule(List.of(MCC_MNC), List.of(GT_PREFIX), REASON);
            final var imsiException = new ImsiRule(List.of(MCC_MNC), List.of(GT_PREFIX), Roaming.ALLOWED, "VIP roamer");
            final var rules = new RoamingRules(List.of(network), Map.of(IMSI, imsiException));

            // WHEN
            underTest.replaceRules(rules);

            // THEN: network block entries are present
            verify(blockRoamingDao).insert(List.of(new RoamingBlockedLocation(null, GT_PREFIX, REASON)));
            verify(blockRoamingDao).insert(List.of(new RoamingBlockedLocationLte(null, MCC, MNC, REASON)));
            // THEN: IMSI exception entries override with "allowed"
            verify(blockRoamingDao).insert(List.of(new RoamingBlockedImsiOverride(null, IMSI, GT_PREFIX, "allowed", "VIP roamer")));
            verify(blockRoamingDao).insert(List.of(new RoamingBlockedImsiOverrideLte(null, IMSI, MCC, MNC, "allowed", "VIP roamer")));
        }

        @Test
        void shouldInsertNetworkBlockWithImsiBlockedException() {
            // GIVEN: roaming is generally open, but one specific IMSI is blocked everywhere
            final var imsiBlock = new ImsiRule(List.of(MCC_MNC), List.of(GT_PREFIX), Roaming.BLOCKED, "fraudster");
            final var rules = new RoamingRules(List.of(), Map.of(IMSI, imsiBlock));

            // WHEN
            underTest.replaceRules(rules);

            // THEN
            verify(blockRoamingDao).insert(List.of(new RoamingBlockedImsiOverride(null, IMSI, GT_PREFIX, "blocked", "fraudster")));
            verify(blockRoamingDao).insert(List.of(new RoamingBlockedImsiOverrideLte(null, IMSI, MCC, MNC, "blocked", "fraudster")));
        }

        @Test
        void shouldMapRoamingEnumToLowercaseDbValue() {
            // GIVEN
            final var defaultRule = new ImsiRule(List.of(), List.of(GT_PREFIX), Roaming.DEFAULT, REASON);
            final var rules = new RoamingRules(List.of(), Map.of(IMSI, defaultRule));

            // WHEN
            underTest.replaceRules(rules);

            // THEN
            verify(blockRoamingDao).insert(List.of(new RoamingBlockedImsiOverride(null, IMSI, GT_PREFIX, "default", REASON)));
        }
    }

    @Nested
    class IsBlocked {

        @Test
        void shouldReturnFalseWhenNoRulesMatch() {
            // GIVEN
            when(blockRoamingDao.findImsiOverrideLte(IMSI, MCC, MNC)).thenReturn(Optional.empty());
            when(blockRoamingDao.isNetworkBlockedLte(MCC, MNC)).thenReturn(false);

            // WHEN
            final var actual = underTest.isBlocked(IMSI, MCC, MNC);

            // THEN
            assertThat(actual).isFalse();
        }

        @Test
        void shouldReturnTrueWhenNetworkIsBlockedAndNoImsiOverrideExists() {
            // GIVEN
            when(blockRoamingDao.findImsiOverrideLte(IMSI, MCC, MNC)).thenReturn(Optional.empty());
            when(blockRoamingDao.isNetworkBlockedLte(MCC, MNC)).thenReturn(true);

            // WHEN
            final var actual = underTest.isBlocked(IMSI, MCC, MNC);

            // THEN
            assertThat(actual).isTrue();
        }

        @Test
        void shouldReturnFalseWhenImsiIsExplicitlyAllowedEvenThoughNetworkIsBlocked() {
            // GIVEN: network is blocked, but this subscriber has an explicit allow override
            final var override = new RoamingBlockedImsiOverrideLte(null, IMSI, MCC, MNC, "allowed", REASON);
            when(blockRoamingDao.findImsiOverrideLte(IMSI, MCC, MNC)).thenReturn(Optional.of(override));

            // WHEN
            final var actual = underTest.isBlocked(IMSI, MCC, MNC);

            // THEN
            assertThat(actual).isFalse();
        }

        @Test
        void shouldReturnTrueWhenImsiIsExplicitlyBlockedEvenThoughNetworkIsNotBlocked() {
            // GIVEN: no general network block, but this subscriber is individually blocked
            final var override = new RoamingBlockedImsiOverrideLte(null, IMSI, MCC, MNC, "blocked", REASON);
            when(blockRoamingDao.findImsiOverrideLte(IMSI, MCC, MNC)).thenReturn(Optional.of(override));

            // WHEN
            final var actual = underTest.isBlocked(IMSI, MCC, MNC);

            // THEN
            assertThat(actual).isTrue();
        }

        @Test
        void shouldFallBackToNetworkRuleWhenImsiOverrideIsDefault() {
            // GIVEN: IMSI override says "use default", and the network is blocked
            final var override = new RoamingBlockedImsiOverrideLte(null, IMSI, MCC, MNC, "default", REASON);
            when(blockRoamingDao.findImsiOverrideLte(IMSI, MCC, MNC)).thenReturn(Optional.of(override));
            when(blockRoamingDao.isNetworkBlockedLte(MCC, MNC)).thenReturn(true);

            // WHEN
            final var actual = underTest.isBlocked(IMSI, MCC, MNC);

            // THEN
            assertThat(actual).isTrue();
        }

        @Test
        void shouldReturnFalseWhenImsiOverrideIsDefaultAndNetworkIsNotBlocked() {
            // GIVEN: IMSI override says "use default", and the network is not blocked
            final var override = new RoamingBlockedImsiOverrideLte(null, IMSI, MCC, MNC, "default", REASON);
            when(blockRoamingDao.findImsiOverrideLte(IMSI, MCC, MNC)).thenReturn(Optional.of(override));
            when(blockRoamingDao.isNetworkBlockedLte(MCC, MNC)).thenReturn(false);

            // WHEN
            final var actual = underTest.isBlocked(IMSI, MCC, MNC);

            // THEN
            assertThat(actual).isFalse();
        }
    }

    @Nested
    class RecordBlocked {

        @Test
        void shouldPersistEventAndIncrementCounter() {
            underTest.recordBlocked(IMSI, MCC);

            verify(blockRoamingDao).insertRoamingBlockedEvent(IMSI, MCC, "hss");
            assertThat(meterRegistry.counter("roaming_blocked", "mcc", MCC, "component", "hss").count())
                .isEqualTo(1.0);
        }
    }

    @Nested
    class RecordAllowed {

        @Test
        void shouldRecordRecoveredAttemptCountAsSingleHistogramSample() {
            when(blockRoamingDao.deleteRoamingBlockedEvents(IMSI, "hss")).thenReturn(3);

            underTest.recordAllowed(IMSI, MCC);

            verify(blockRoamingDao).deleteRoamingBlockedEvents(IMSI, "hss");
            final var summary = meterRegistry.find("roaming_blocked_recovered")
                .tags("mcc", MCC, "component", "hss").summary();
            assertThat(summary).isNotNull();
            assertThat(summary.count()).isEqualTo(1L);
            assertThat(summary.totalAmount()).isEqualTo(3.0);
            assertThat(summary.takeSnapshot().histogramCounts())
                .anySatisfy(bucket -> {
                    assertThat(bucket.bucket()).isEqualTo(3.0);
                    assertThat(bucket.count()).isEqualTo(1.0);
                });
        }

        @Test
        void shouldNotRecordAnythingWhenNoEventsWereDeleted() {
            when(blockRoamingDao.deleteRoamingBlockedEvents(IMSI, "hss")).thenReturn(0);

            underTest.recordAllowed(IMSI, MCC);

            verify(blockRoamingDao).deleteRoamingBlockedEvents(IMSI, "hss");
            assertThat(meterRegistry.find("roaming_blocked_recovered").summary()).isNull();
        }
    }

    @Nested
    class ExpireOldBlockedEvents {

        private static final String OTHER_IMSI = "987654321098765";
        private static final String OTHER_MCC = "234";

        @Test
        void shouldEmitNothingWhenNothingExpired() {
            when(blockRoamingDao.deleteExpiredRoamingBlockedEvents("hss")).thenReturn(List.of());

            underTest.expireOldBlockedEvents();

            assertThat(meterRegistry.find("roaming_blocked_not_recovered").summary()).isNull();
        }

        @Test
        void shouldRecordAttemptCountPerImsiAndMccAsHistogramSamples() {
            final Instant now = Instant.now();
            when(blockRoamingDao.deleteExpiredRoamingBlockedEvents("hss")).thenReturn(List.of(
                new RoamingBlockedEvent(IMSI, MCC, "hss", now),
                new RoamingBlockedEvent(IMSI, MCC, "hss", now),
                new RoamingBlockedEvent(IMSI, MCC, "hss", now),
                new RoamingBlockedEvent(OTHER_IMSI, MCC, "hss", now),
                new RoamingBlockedEvent(IMSI, OTHER_MCC, "hss", now)));

            underTest.expireOldBlockedEvents();

            // MCC sees two IMSIs: one with three attempts, one with a single attempt.
            final var summaryMcc = meterRegistry.find("roaming_blocked_not_recovered")
                .tags("mcc", MCC, "component", "hss").summary();
            assertThat(summaryMcc).isNotNull();
            assertThat(summaryMcc.count()).isEqualTo(2L);
            assertThat(summaryMcc.totalAmount()).isEqualTo(4.0);

            // OTHER_MCC sees a single IMSI with a single attempt.
            final var summaryOther = meterRegistry.find("roaming_blocked_not_recovered")
                .tags("mcc", OTHER_MCC, "component", "hss").summary();
            assertThat(summaryOther).isNotNull();
            assertThat(summaryOther.count()).isEqualTo(1L);
            assertThat(summaryOther.totalAmount()).isEqualTo(1.0);
        }
    }

    private static BlockRoamingDao newBlockRoamingDao(final EntityManager entityManager) {
        try {
            final var constructor = BlockRoamingDao.class.getDeclaredConstructor(EntityManager.class);
            constructor.setAccessible(true);
            return constructor.newInstance(entityManager);
        } catch (final ReflectiveOperationException e) {
            throw new RuntimeException("Could not create BlockRoamingDao for integration test", e);
        }
    }
}
