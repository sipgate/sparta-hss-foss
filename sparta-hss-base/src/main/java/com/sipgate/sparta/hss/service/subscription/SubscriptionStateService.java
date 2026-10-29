package com.sipgate.sparta.hss.service.subscription;

import com.sipgate.sparta.hss.event.EventPublisher;
import com.sipgate.sparta.hss.persistence.SimDao;
import com.sipgate.sparta.hss.persistence.entities.Imsi;
import jakarta.transaction.Transactional;
import java.util.NoSuchElementException;
import java.util.Objects;

/// Activates or deactivates a SIM's subscription by ICCID.
///
/// Deactivation closes the subscriber's live registrations in all access networks, so a barred SIM
/// is cut off immediately instead of at the next registration attempt. The service flips the
/// switch and publishes one [SimDeactivated] carrying only the subscriber's identity — each
/// outbound listener fetches the peer from its own table and removes it after sending, so the
/// service knows nothing about interfaces or requests:
///
/// - **4G (S6a):** CLR with Cancellation-Type `SUBSCRIPTION_WITHDRAWAL` to the stored MME — the
///   HSS-initiated detach of TS 23.401 §5.3.8.4, without the re-attach flag.
/// - **IMS (Cx):** RTR with Reason-Code `PERMANENT_TERMINATION` to the stored S-CSCF
///   (TS 29.228 §6.1.3.1).
/// - **VoWiFi (SWx):** RTR with Reason-Code `PERMANENT_TERMINATION` to the stored 3GPP AAA
///   server (TS 29.273 §8.1.2.2.3).
/// - **IP-SM-GW:** the stored entry is removed; no signalling is required.
///
/// The event is published inside the transaction and processed fire-and-forget like all outbound
/// requests of the HSS. Because each listener owns its table, the registration rows are removed
/// **outside** this transaction: if it fails after publishing, the peers may have detached the
/// subscriber while `sim.isActive` is unchanged, or rows may be removed without a request sent —
/// both self-healing (the device re-registers while `sim.isActive` is unchanged; a re-run of the
/// deactivation finds nothing left to send).
///
/// Interfaces without a stored registration send nothing, so repeated deactivation is idempotent.
/// An interface not enabled in `sipgate.diameter.capabilities` has no listener, so its rows stay —
/// stale but harmless (4G access is barred, so the peer registration is unreachable).
///
/// Known limit: deactivation does not block **new** Cx/SWx registrations — the SAR paths check no
/// subscription state, so a phone can re-register for IMS/VoWiFi until its next (rejected) 4G/EPC
/// authentication. Gating those paths on `sim.isActive` is a separate concern.
public class SubscriptionStateService {

    private final SimDao simDao;
    private final EventPublisher eventPublisher;

    public SubscriptionStateService(final SimDao simDao, final EventPublisher eventPublisher) {
        this.simDao = simDao;
        this.eventPublisher = eventPublisher;
    }

    /// Activates the SIM: only flips the switch, nothing to tear down or notify.
    ///
    /// @throws NoSuchElementException if no SIM exists for the ICCID
    @Transactional
    public void activate(final String iccid) {
        final var sim = simDao.getSimByIccid(Objects.requireNonNull(iccid))
            .orElseThrow(() -> new NoSuchElementException("no sim for iccid " + iccid));
        sim.setActive(true);
    }

    /// Deactivates the SIM and ends the registrations of all its IMSIs (see class doc).
    ///
    /// @throws NoSuchElementException if no SIM exists for the ICCID
    @Transactional
    public void deactivate(final String iccid) {
        final var sim = simDao.getSimByIccid(Objects.requireNonNull(iccid))
            .orElseThrow(() -> new NoSuchElementException("no sim for iccid " + iccid));
        sim.setActive(false);

        eventPublisher.publish(new SimDeactivated(
            iccid,
            sim.getMsisdn().getMsisdn(),
            sim.getImsis().stream().map(Imsi::getImsi).toList()));
    }

}
