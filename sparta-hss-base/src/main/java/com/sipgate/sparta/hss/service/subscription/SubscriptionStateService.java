package com.sipgate.sparta.hss.service.subscription;

import com.sipgate.sparta.hss.event.EventPublisher;
import com.sipgate.sparta.hss.persistence.SimDao;
import com.sipgate.sparta.hss.persistence.entities.Imsi;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.NoSuchElementException;
import java.util.Objects;

/// Activates or deactivates a SIM's subscription by ICCID; deactivation ends the subscriber's
/// live registrations (see [SimDeactivated] and its listeners).
public class SubscriptionStateService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionStateService.class);

    private final SimDao simDao;
    private final EventPublisher eventPublisher;

    public SubscriptionStateService(final SimDao simDao, final EventPublisher eventPublisher) {
        this.simDao = simDao;
        this.eventPublisher = eventPublisher;
    }

    /// @throws NoSuchElementException if no SIM exists for the ICCID
    @Transactional
    public void activate(final String iccid) {
        final var sim = simDao.getSimByIccid(Objects.requireNonNull(iccid))
            .orElseThrow(() -> new NoSuchElementException("no sim for iccid " + iccid));
        sim.setActive(true);
    }

    /// @throws NoSuchElementException if no SIM exists for the ICCID
    @Transactional
    public void deactivate(final String iccid) {
        final var sim = simDao.getSimByIccid(Objects.requireNonNull(iccid))
            .orElseThrow(() -> new NoSuchElementException("no sim for iccid " + iccid));
        sim.setActive(false);

        try {
            eventPublisher.publish(new SimDeactivated(
                iccid,
                sim.getMsisdn().getMsisdn(),
                sim.getImsis().stream().map(Imsi::getImsi).toList()));
        } catch (final Exception e) {
            LOGGER.error("error during publishing SimDeactivated for SIM {}", sim, e);
        }
    }

}
