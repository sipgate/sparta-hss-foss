package com.sipgate.sparta.hss.service.roaming;

import com.sipgate.sparta.hss.persistence.BlockRoamingDao;
import com.sipgate.sparta.hss.persistence.entities.RoamingBlockedEntity;
import com.sipgate.sparta.hss.persistence.entities.RoamingBlockedImsiOverride;
import com.sipgate.sparta.hss.persistence.entities.RoamingBlockedImsiOverrideLte;
import com.sipgate.sparta.hss.persistence.entities.RoamingBlockedLocation;
import com.sipgate.sparta.hss.persistence.entities.RoamingBlockedLocationLte;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class BlockRoamingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BlockRoamingService.class);
    private static final String COMPONENT_TAG_NAME = "component";
    private static final String COMPONENT_TAG_VALUE = "hss";

    private final BlockRoamingDao blockRoamingDao;
    private final MeterRegistry meterRegistry;

    public BlockRoamingService(final BlockRoamingDao blockRoamingDao, final MeterRegistry meterRegistry) {
        this.blockRoamingDao = blockRoamingDao;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public void replaceRules(final RoamingRules rules) {
        final var locations = rules.networks().stream()
                .flatMap(n -> n.gtPrefix().stream()
                        .map(gt -> new RoamingBlockedLocation(null, gt, n.reason())))
                .toList();

        final var locationsLte = rules.networks().stream()
                .flatMap(n -> n.mccMnc().stream()
                        .map(mccMnc -> mccMnc.split("-", 2))
                        .map(parts -> new RoamingBlockedLocationLte(null, parts[0], parts[1], n.reason())))
                .toList();

        final var imsiOverrides = rules.imsis().entrySet().stream()
                .flatMap(e -> e.getValue().gtPrefix().stream()
                        .map(gt -> new RoamingBlockedImsiOverride(null, e.getKey(), gt, roamingEnumToDb(e.getValue().roaming()), e.getValue().reason())))
                .toList();

        final var imsiOverridesLte = rules.imsis().entrySet().stream()
                .flatMap(e -> e.getValue().mccMnc().stream()
                        .map(mccMnc -> mccMnc.split("-", 2))
                        .map(parts -> new RoamingBlockedImsiOverrideLte(null, e.getKey(), parts[0], parts[1], roamingEnumToDb(e.getValue().roaming()), e.getValue().reason())))
                .toList();

        blockRoamingDao
                .deleteAll()
                .insert(imsiOverrides)
                .insert(imsiOverridesLte)
                .insert(locations)
                .insert(locationsLte);
    }

    /// The lowercase strings are the database representation (matched again in [#isBlocked]). The
    /// mapping stays out of the enum so the domain type carries no storage concern.
    private String roamingEnumToDb(final ImsiRule.Roaming roaming) {
        return switch (roaming) {
            case BLOCKED -> "blocked";
            case ALLOWED -> "allowed";
            case DEFAULT -> "default";
        };
    }

    public Map<String, List<? extends RoamingBlockedEntity>> dumpAll() {
        return blockRoamingDao.dumpAll();
    }

    public boolean isBlocked(final String imsi, final String mcc, final String mnc) {
        final var override = blockRoamingDao.findImsiOverrideLte(imsi, mcc, mnc);

        return switch (override.map(RoamingBlockedImsiOverrideLte::getRoaming).orElse("default")) {
            case "allowed" -> false;
            case "blocked" -> true;
            default -> blockRoamingDao.isNetworkBlockedLte(mcc, mnc);
        };
    }

    public void recordBlocked(final String imsi, final String mcc) {
        blockRoamingDao.insertRoamingBlockedEvent(imsi, mcc, COMPONENT_TAG_VALUE);
        meterRegistry.counter("roaming_blocked", "mcc", mcc, COMPONENT_TAG_NAME, COMPONENT_TAG_VALUE).increment();
    }

    public void recordAllowed(final String imsi, final String mcc) {
        final var recoveredAttempts = blockRoamingDao.deleteRoamingBlockedEvents(imsi, COMPONENT_TAG_VALUE);
        if (recoveredAttempts <= 0) {
            return;
        }
        recordAttempts("roaming_blocked_recovered", mcc, recoveredAttempts);
    }

    /// Callers must invoke this periodically (the Spring deployment schedules it every 5 minutes);
    /// expiry turns stale blocked events into the roaming_blocked_not_recovered metric.
    public void expireOldBlockedEvents() {
        final var expired = blockRoamingDao.deleteExpiredRoamingBlockedEvents(COMPONENT_TAG_VALUE);
        if (expired.isEmpty()) {
            return;
        }
        final var attemptsPerImsiAndMcc = expired.stream().collect(Collectors.groupingBy(
            event -> new AttemptGroup(event.getImsi(), event.getMcc()),
            Collectors.counting()));
        attemptsPerImsiAndMcc.forEach((group, attempts) ->
            recordAttempts("roaming_blocked_not_recovered", group.mcc(), attempts));
        LOGGER.info("Expired {} roaming-blocked event(s) across {} IMSI/MCC group(s)",
            expired.size(), attemptsPerImsiAndMcc.size());
    }

    private void recordAttempts(final String metricName, final String mcc, final long attempts) {
        DistributionSummary.builder(metricName)
            .serviceLevelObjectives(1, 2, 3, 4, 5)
            .tags("mcc", mcc, COMPONENT_TAG_NAME, COMPONENT_TAG_VALUE)
            .register(meterRegistry)
            .record(attempts);
    }

    private record AttemptGroup(String imsi, String mcc) {
    }
}
