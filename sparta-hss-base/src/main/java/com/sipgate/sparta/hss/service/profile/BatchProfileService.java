package com.sipgate.sparta.hss.service.profile;

import com.sipgate.sparta.hss.persistence.EffectiveProfileDao;
import com.sipgate.sparta.hss.persistence.EffectiveProfileDao.UpdateProfileResult;
import com.sipgate.sparta.hss.persistence.ImsiProfileDao;
import com.sipgate.sparta.hss.persistence.TacProfileDao;
import jakarta.transaction.Transactional;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class BatchProfileService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BatchProfileService.class);

    private final ImsiProfileDao imsiProfileDao;

    private final TacProfileDao tacProfileDao;

    private final ProfileService profileService;

    private final EffectiveProfileDao effectiveProfileDao;

    public BatchProfileService(
            final ImsiProfileDao imsiProfileDao,
            final TacProfileDao tacProfileDao,
            final ProfileService profileService,
            final EffectiveProfileDao effectiveProfileDao) {
        this.imsiProfileDao = imsiProfileDao;
        this.tacProfileDao = tacProfileDao;
        this.profileService = profileService;
        this.effectiveProfileDao = effectiveProfileDao;
    }

    private static List<UpdateProfileResult> getProfileDiff(
            final Map<String, UpdateProfileResult> newUpdateProfileResultMap,
            final Map<String, UpdateProfileResult> oldUpdateProfileResultMap) {
        // keep the entries that have changed and the new entries; remove the entries that disappeared.
        final var result = new ArrayList<UpdateProfileResult>();
        newUpdateProfileResultMap.entrySet().stream()
                .filter(entry -> {
                    final var old = oldUpdateProfileResultMap.get(entry.getKey());
                    return old == null || !old.getProfile().equals(entry.getValue().getProfile());
                })
                .map(Map.Entry::getValue)
                .forEach(result::add);
        // for all entries that have disappeared, return 'default'
        oldUpdateProfileResultMap.entrySet().stream()
                .filter(entry -> !newUpdateProfileResultMap.containsKey(entry.getKey()))
                .map(e -> {
                    final var v = e.getValue();
                    return new UpdateProfileResult(e.getKey(), v.getMmeHostname(), v.getMmeRealm(), v.getMsisdn(), "default", v.getVisitedPlmnId());
                })
                .forEach(result::add);
        return result;
    }

    /**
     * @param newState key is IMSI, value is the profile name without ".xml" containing the UpdateLocationAnswer element
     *                 <example>
     *                 { "023455" : "with-ims-live", "011223": "default", "7734234" : "ims-dev" }
     *                 </example>
     */
    @Transactional
    public void applyNewImsiState(final Map<String, String> newState)
            throws InterruptedException {
        LOGGER.info("Applying new IMSI state: size:{}", newState.size());
        final var oldImsiProfiles = imsiProfileDao.findAll();

        final var oldUpdateProfileResultMap = getEffectiveProfilesMap(
                oldImsiProfiles.keySet(),
                new HashSet<>());

        updateDatabase(newState, oldImsiProfiles, imsiProfileDao::delete, imsiProfileDao::saveOrUpdate);
        final var newUpdateProfileResultMap = getEffectiveProfilesMap(
                newState.keySet(),
                new HashSet<>());
        final var diffUpdateProfileResult = getProfileDiff(
                newUpdateProfileResultMap,
                oldUpdateProfileResultMap);

        profileService.updateProfiles(diffUpdateProfileResult);

    }

    @Transactional
    public void applyNewTacState(final Map<String, String> newState) throws InterruptedException {
        LOGGER.info("Applying new TAC state: size:{}", newState.size());
        final var imsiProfiles = imsiProfileDao.findAll();
        final var oldTacProfiles = tacProfileDao.findAll();

        final var oldUpdateProfileResultMap = getEffectiveProfilesMap(
                imsiProfiles.keySet(),
                oldTacProfiles.keySet());

        updateDatabase(newState, oldTacProfiles, tacProfileDao::delete, tacProfileDao::saveOrUpdate);
        final var newUpdateProfileResultMap = getEffectiveProfilesMap(
                imsiProfiles.keySet(),
                newState.keySet());
        final var diffUpdateProfileResult = getProfileDiff(
                newUpdateProfileResultMap,
                oldUpdateProfileResultMap);

        profileService.updateProfiles(diffUpdateProfileResult);
    }

    private Map<String, UpdateProfileResult> getEffectiveProfilesMap(final Set<String> imsis, final Set<String> tacs) {
        return effectiveProfileDao
                .getUpdateProfileResults(imsis, tacs).stream()
                .collect(Collectors.toMap(UpdateProfileResult::getImsi, item -> item));
    }

    private void updateDatabase(
            final Map<String, String> newState,
            final Map<String, String> oldState,
            final Consumer<String> deleteConsumer,
            final BiConsumer<String, String> saveOrUpdateConsumer) {
        final var countDel = new AtomicInteger();
        final var countChg = new AtomicInteger();
        final var countNew = new AtomicInteger();
        final var countErr = new AtomicInteger();
        oldState.keySet().forEach(key -> {
            if (!newState.containsKey(key)) {
                LOGGER.trace("delete imsiProfile for:{}", key);
                deleteConsumer.accept(key);
                countDel.incrementAndGet();
                return;
            }

            if (!newState.get(key).equals(oldState.get(key))) {
                LOGGER.trace("saveOrUpdate imsiProfile for:{}", key);
                saveOrUpdateConsumer.accept(key, newState.get(key));
                countChg.incrementAndGet();
            }
        });

        newState.forEach((key, profileName) -> {
            if (!oldState.containsKey(key)) {
                LOGGER.trace("re-saveOrUpdate imsiProfile for:{}", key);
                try {
                    saveOrUpdateConsumer.accept(key, profileName);
                    countNew.incrementAndGet();
                } catch (final RuntimeException e) {
                    LOGGER.warn("could not re-saveOrUpdate imsiProfile for:{}->{}: {}", key, profileName, e.getMessage(), e);
                    countErr.incrementAndGet();
                }
            }
        });
        LOGGER.info("> Database update: deleted:{} changed:{} new:{} error:{}",
                countDel.get(), countChg.get(), countNew.get(), countErr.get());
    }
}


